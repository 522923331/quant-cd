package com.quantcd.ashare.dbdata;

import com.quantcd.ashare.tushare.AshareSettings;
import com.quantcd.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SyncJobService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SyncJobService.class);

    private final AshareStore store;
    private final AshareSettings settings;
    private final ScreenDataSyncService screenSync;
    private final DailyBarSyncService dailySync;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "ashare-sync");
            thread.setDaemon(false);
            return thread;
        }
    });

    public SyncJobService(AshareStore store, AshareSettings settings, ScreenDataSyncService screenSync, DailyBarSyncService dailySync) {
        this.store = store;
        this.settings = settings;
        this.screenSync = screenSync;
        this.dailySync = dailySync;
    }

    public AshareStore.SyncJob startScreen(String token, String codes) {
        requireToken();
        if (codes != null && codes.trim().isEmpty()) {
            codes = null;
        }
        final String codesText = codes;
        Begun begun = begin(token, "SCREEN_DATA");
        if (!begun.created) {
            log.info("筛选同步任务 {} 已存在，不再重复执行", begun.job.id);
            return begun.job;
        }
        final long id = begun.job.id;
        log.info("筛选同步任务 {} 开始，代码 {}", id, codesText == null ? "全部上市股票" : codesText);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    screenSync.run(id, codesText);
                    store.finishJob(id, Instant.now().toString());
                    log.info("筛选同步任务 {} 完成", id);
                } catch (Exception ex) {
                    String message = ex.getMessage() == null ? "同步失败" : ex.getMessage();
                    store.failJob(id, message, Instant.now().toString());
                    log.warn("筛选同步任务 {} 失败: {}", id, message);
                }
            }
        });
        return store.findJob(id);
    }

    public AshareStore.SyncJob startDaily(String token, final String tsCode, final String start, final String end) {
        requireToken();
        Begun begun = begin(token, "DAILY_BAR");
        if (!begun.created) {
            log.info("日线同步任务 {} 已存在，不再重复执行", begun.job.id);
            return begun.job;
        }
        final long id = begun.job.id;
        log.info("日线同步任务 {} 开始，{} {}-{}", id, tsCode, start, end);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    dailySync.run(id, tsCode, start, end);
                    store.finishJob(id, Instant.now().toString());
                    log.info("日线同步任务 {} 完成", id);
                } catch (Exception ex) {
                    String message = ex.getMessage() == null ? "同步失败" : ex.getMessage();
                    store.failJob(id, message, Instant.now().toString());
                    log.warn("日线同步任务 {} 失败: {}", id, message);
                }
            }
        });
        return store.findJob(id);
    }

    private Begun begin(String token, String type) {
        if (token == null || token.trim().isEmpty()) {
            throw new BizException("缺少提交令牌");
        }
        AshareStore.SyncJob existing = store.findJobByToken(token);
        if (existing != null) {
            return new Begun(existing, false);
        }
        try {
            long id = store.insertJob(token, type, Instant.now().toString());
            return new Begun(store.findJob(id), true);
        } catch (DataIntegrityViolationException ex) {
            existing = store.findJobByToken(token);
            if (existing != null) {
                return new Begun(existing, false);
            }
            AshareStore.SyncJob running = store.findRunning();
            if (running != null) {
                log.warn("已有同步任务 {} 正在运行", running.id);
                throw new BizException("已有同步任务正在运行");
            }
            throw ex;
        }
    }

    private void requireToken() {
        if (!settings.hasToken()) {
            throw new BizException("未配置 Tushare Token");
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    private static final class Begun {
        private final AshareStore.SyncJob job;
        private final boolean created;

        private Begun(AshareStore.SyncJob job, boolean created) {
            this.job = job;
            this.created = created;
        }
    }
}

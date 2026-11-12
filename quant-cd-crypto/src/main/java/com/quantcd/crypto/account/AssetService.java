package com.quantcd.crypto.account;

import com.quantcd.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9]{2,20}$");

    private final AssetStore store;

    public AssetService(AssetStore store) {
        this.store = store;
    }

    public List<String> extras() {
        return store.list();
    }

    public List<WatchedAsset> watched() {
        List<WatchedAsset> rows = new ArrayList<WatchedAsset>();
        for (AssetStore.Row row : store.rows()) {
            rows.add(new WatchedAsset(row.asset, row.costPrice));
        }
        return rows;
    }

    public List<String> displayAssets() {
        List<String> assets = new ArrayList<String>();
        assets.add("USDT");
        assets.add("USDC");
        for (String extra : store.list()) {
            if (!assets.contains(extra)) {
                assets.add(extra);
            }
        }
        return assets;
    }

    public void add(String raw) {
        String asset = normalize(raw);
        if ("USDT".equals(asset) || "USDC".equals(asset)) {
            throw new BizException("USDT 和 USDC 已默认展示");
        }
        try {
            store.insert(asset, Instant.now().toString());
        } catch (DataIntegrityViolationException ex) {
            throw new BizException("币种已存在");
        }
        log.info("已添加关注币种 {}", asset);
    }

    public void remove(String raw) {
        String asset = normalize(raw);
        if ("USDT".equals(asset) || "USDC".equals(asset)) {
            throw new BizException("默认币种不能删除");
        }
        if (store.delete(asset) == 0) {
            throw new BizException("币种不在配置里");
        }
        log.info("已移除关注币种 {}", asset);
    }

    public static String normalize(String raw) {
        if (raw == null) {
            throw new BizException("请填写币种");
        }
        String asset = raw.trim().toUpperCase();
        if (!CODE.matcher(asset).matches()) {
            throw new BizException("币种代码为 2 到 20 位字母或数字");
        }
        return asset;
    }
}

package com.quantcd.web;

import com.quantcd.ashare.dbdata.AshareStore;
import com.quantcd.ashare.dbdata.SyncJobService;
import com.quantcd.ashare.tushare.AshareSettings;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
public class AshareDataController {

    private final AshareStore store;
    private final SyncJobService jobs;
    private final AshareSettings settings;

    public AshareDataController(AshareStore store, SyncJobService jobs, AshareSettings settings) {
        this.store = store;
        this.jobs = jobs;
        this.settings = settings;
    }

    @GetMapping("/ashare/data")
    public String page(@RequestParam(required = false) Long job, @RequestParam(required = false) String code, Model model) {
        fill(model, job);
        if (code != null && code.trim().length() > 0) {
            model.addAttribute("code", code.trim().toUpperCase());
            model.addAttribute("bars", store.recentDaily(code.trim().toUpperCase(), 30));
        }
        return "ashare-data";
    }

    @PostMapping("/ashare/sync/screen")
    public String syncScreen(@RequestParam String submitToken, @RequestParam(required = false) String codes,
                             @RequestParam(defaultValue = "data") String back, RedirectAttributes redirect) {
        AshareStore.SyncJob job = jobs.startScreen(submitToken, codes);
        redirect.addFlashAttribute("success", "同步任务已提交");
        String page = "screen".equals(back) ? "/ashare/screen" : "/ashare/data";
        return "redirect:" + page + "?job=" + job.id;
    }

    @PostMapping("/ashare/sync/daily")
    public String syncDaily(@RequestParam String submitToken, @RequestParam String tsCode,
                            @RequestParam String start, @RequestParam String end,
                            @RequestParam(defaultValue = "data") String back, RedirectAttributes redirect) {
        AshareStore.SyncJob job = jobs.startDaily(submitToken, tsCode, start, end);
        redirect.addFlashAttribute("success", "日线同步已提交");
        String page = "backtest".equals(back) ? "/ashare/backtest" : "/ashare/data";
        return "redirect:" + page + "?job=" + job.id;
    }

    @GetMapping("/ashare/sync/{id}")
    @ResponseBody
    public ResponseEntity<AshareStore.SyncJob> status(@PathVariable long id) {
        AshareStore.SyncJob job = store.findJob(id);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(job);
    }

    static void fill(org.springframework.ui.Model model, Long jobId, AshareStore store, AshareSettings settings) {
        model.addAttribute("tokenConfigured", settings.hasToken());
        model.addAttribute("screenToken", UUID.randomUUID().toString());
        model.addAttribute("dailyToken", UUID.randomUUID().toString());
        if (jobId != null) {
            model.addAttribute("job", store.findJob(jobId));
        }
    }

    private void fill(Model model, Long job) {
        fill(model, job, store, settings);
    }
}

package com.quantcd.web;

import com.quantcd.ashare.backtest.BacktestService;
import com.quantcd.ashare.dbdata.AshareStore;
import com.quantcd.ashare.tushare.AshareSettings;
import com.quantcd.common.exception.BizException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.UUID;

@Controller
public class AshareBacktestController {

    private final BacktestService backtests;
    private final AshareStore store;
    private final AshareSettings settings;

    public AshareBacktestController(BacktestService backtests, AshareStore store, AshareSettings settings) {
        this.backtests = backtests;
        this.store = store;
        this.settings = settings;
    }

    @GetMapping("/ashare/backtest")
    public String page(@RequestParam(required = false) Long id, @RequestParam(required = false) Long job, Model model) {
        AshareDataController.fill(model, job, store, settings);
        model.addAttribute("codes", store.codesWithDaily());
        model.addAttribute("commission", settings.getCommissionRate().toPlainString());
        model.addAttribute("stamp", settings.getStampTaxRate().toPlainString());
        model.addAttribute("backtestToken", UUID.randomUUID().toString());
        if (id != null) {
            model.addAttribute("backtest", store.findBacktest(id));
        }
        return "ashare-backtest";
    }

    @PostMapping("/ashare/backtest")
    public String run(@RequestParam String submitToken, @RequestParam String tsCode, @RequestParam String start,
                      @RequestParam String end, @RequestParam int fast, @RequestParam int slow,
                      @RequestParam String cash, RedirectAttributes redirect) {
        BigDecimal initial;
        try {
            initial = new BigDecimal(cash.trim());
        } catch (RuntimeException ex) {
            throw new BizException("初始资金格式不正确");
        }
        AshareStore.BacktestRow row = backtests.run(submitToken, tsCode, start, end, fast, slow, initial);
        redirect.addFlashAttribute("success", "回测完成");
        return "redirect:/ashare/backtest?id=" + row.id;
    }

    @GetMapping(value = "/ashare/backtest/{id}/equity", produces = "application/json")
    @ResponseBody
    public ResponseEntity<String> equity(@PathVariable long id) {
        AshareStore.BacktestRow row = store.findBacktest(id);
        if (row == null || row.equityJson == null) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("[]");
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(row.equityJson);
    }
}

package com.quantcd.web;

import com.quantcd.ashare.dbdata.AshareStore;
import com.quantcd.ashare.screen.ScreenService;
import com.quantcd.ashare.tushare.AshareSettings;
import com.quantcd.common.exception.BizException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.UUID;

@Controller
public class AshareScreenController {

    private final ScreenService screens;
    private final AshareStore store;
    private final AshareSettings settings;

    public AshareScreenController(ScreenService screens, AshareStore store, AshareSettings settings) {
        this.screens = screens;
        this.store = store;
        this.settings = settings;
    }

    @GetMapping("/ashare/screen")
    public String page(@RequestParam(required = false) Long run, @RequestParam(required = false) String code,
                       @RequestParam(required = false) Long job, Model model) {
        AshareDataController.fill(model, job, store, settings);
        AshareStore.ScreenRun screenRun = run == null ? store.latestScreen() : store.findScreen(run);
        model.addAttribute("screenRun", screenRun);
        model.addAttribute("lookup", code);
        if (screenRun != null) {
            boolean lookup = code != null && code.trim().length() > 0;
            model.addAttribute("hits", store.hits(screenRun.id, lookup ? code.trim().toUpperCase() : null, !lookup));
            model.addAttribute("passCount", store.countOutcome(screenRun.id, "PASS"));
            model.addAttribute("failCount", store.countOutcome(screenRun.id, "FAIL"));
            model.addAttribute("skipCount", store.countOutcome(screenRun.id, "SKIP"));
        }
        model.addAttribute("screenSubmitToken", UUID.randomUUID().toString());
        return "ashare-screen";
    }

    @PostMapping("/ashare/screen")
    public String screen(@RequestParam String submitToken, @RequestParam(defaultValue = "5") int years,
                         @RequestParam(defaultValue = "false") boolean wantReturn,
                         @RequestParam(defaultValue = "false") boolean wantDividend,
                         @RequestParam(defaultValue = "false") boolean wantProfit,
                         @RequestParam(required = false) String minNetMargin,
                         @RequestParam(required = false) String minGrossMargin,
                         @RequestParam(required = false) String minRoe,
                         @RequestParam(required = false) String minOcfToProfit,
                         RedirectAttributes redirect) {
        AshareStore.ScreenRun run = screens.screen(submitToken, years, wantReturn, wantDividend, wantProfit,
                floor(minNetMargin, "净利率"), floor(minGrossMargin, "毛利率"), floor(minRoe, "ROE"),
                floor(minOcfToProfit, "净现比"));
        redirect.addFlashAttribute("success", "筛选完成，通过 " + run.passCount + " 只");
        return "redirect:/ashare/screen?run=" + run.id;
    }

    private static BigDecimal floor(String text, String label) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(label + "下限必须是数字");
        }
    }
}

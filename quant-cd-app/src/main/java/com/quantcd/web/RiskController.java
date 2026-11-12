package com.quantcd.web;

import com.quantcd.common.exception.BizException;
import com.quantcd.crypto.risk.RiskConfig;
import com.quantcd.crypto.risk.RiskConfigStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Instant;

@Controller
public class RiskController {

    private static final Logger log = LoggerFactory.getLogger(RiskController.class);

    private final RiskConfigStore store;

    public RiskController(RiskConfigStore store) {
        this.store = store;
    }

    @GetMapping("/risk")
    public String page(Model model) {
        model.addAttribute("risk", store.load());
        return "risk";
    }

    @PostMapping("/risk")
    public String save(@RequestParam String maxNotional, @RequestParam String maxQty,
                       @RequestParam int maxOrdersPerMinute, @RequestParam long minIntervalMs,
                       RedirectAttributes redirect) {
        BigDecimal notional = decimal(maxNotional);
        BigDecimal qty = decimal(maxQty);
        if (notional.signum() <= 0 || qty.signum() <= 0 || maxOrdersPerMinute <= 0 || minIntervalMs < 0) {
            throw new BizException("风控参数必须大于 0");
        }
        store.save(new RiskConfig(notional, qty, maxOrdersPerMinute, minIntervalMs), Instant.now().toString());
        log.info("风控已更新，单笔金额 {}，单笔数量 {}，每分钟 {} 次，间隔 {} 毫秒",
                notional.toPlainString(), qty.toPlainString(), maxOrdersPerMinute, minIntervalMs);
        redirect.addFlashAttribute("success", "风控已保存，下一笔订单生效");
        return "redirect:/risk";
    }

    private static BigDecimal decimal(String text) {
        try {
            return new BigDecimal(text.trim());
        } catch (RuntimeException ex) {
            throw new BizException("风控数字格式不正确");
        }
    }
}

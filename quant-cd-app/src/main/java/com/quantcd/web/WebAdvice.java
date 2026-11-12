package com.quantcd.web;

import com.quantcd.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;

@ControllerAdvice
public class WebAdvice {

    private static final Logger log = LoggerFactory.getLogger(WebAdvice.class);

    @ExceptionHandler(BizException.class)
    public String handle(BizException ex, HttpServletRequest request, RedirectAttributes redirect) {
        log.warn("{} {} : {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        redirect.addFlashAttribute("error", ex.getMessage());
        String referer = request.getHeader("Referer");
        if (referer == null) {
            return "redirect:/";
        }
        try {
            URI uri = URI.create(referer);
            String path = uri.getRawPath();
            if (path == null || !path.startsWith("/")) {
                return "redirect:/";
            }
            String query = uri.getRawQuery();
            return "redirect:" + path + (query == null ? "" : "?" + query);
        } catch (IllegalArgumentException ignored) {
            return "redirect:/";
        }
    }
}

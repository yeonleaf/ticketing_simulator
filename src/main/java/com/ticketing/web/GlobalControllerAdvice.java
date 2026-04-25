package com.ticketing.web;

import com.ticketing.global.config.ThreadConfiguration;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final ThreadConfiguration threadConfiguration;

    public GlobalControllerAdvice(ThreadConfiguration threadConfiguration) {
        this.threadConfiguration = threadConfiguration;
    }

    @ModelAttribute
    public void addGlobalAttributes(Model model) {
        model.addAttribute("threadType", threadConfiguration.getThreadType());
        model.addAttribute("isVirtualThread", threadConfiguration.isEnabled());
    }
}

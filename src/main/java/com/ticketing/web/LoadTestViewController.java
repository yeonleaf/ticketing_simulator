package com.ticketing.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class LoadTestViewController {

    @GetMapping("/load-test/{id}")
    public String loadTestResult(@PathVariable Long id, Model model) {
        model.addAttribute("loadTestId", id);
        return "loadtest/result";
    }
}

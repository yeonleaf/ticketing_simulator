package com.ticketing.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ShowViewController {

    @GetMapping("/")
    public String index() {
        return "redirect:/simulations";
    }
}

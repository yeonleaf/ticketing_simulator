package com.ticketing.web;

import com.ticketing.domain.show.ShowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class ShowViewController {

    private final ShowService showService;

    @GetMapping("/")
    public String index() {
        return "redirect:/shows";
    }

    @GetMapping("/shows")
    public String showList(Model model) {
        model.addAttribute("shows", showService.getAllShows());
        return "show/list";
    }

    @GetMapping("/shows/new")
    public String showNewForm() {
        return "show/new";
    }

    @GetMapping("/shows/{id}")
    public String showDetail(@PathVariable Long id, Model model) {
        model.addAttribute("show", showService.getShow(id));
        return "show/detail";
    }
}

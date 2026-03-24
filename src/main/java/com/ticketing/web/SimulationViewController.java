package com.ticketing.web;

import com.ticketing.domain.simulation.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class SimulationViewController {

    private final SimulationService simulationService;

    @GetMapping("/simulations")
    public String simulationList(Model model) {
        model.addAttribute("simulations", simulationService.getAllSimulations());
        return "simulation/list";
    }

    @GetMapping("/simulations/{id}/run")
    public String simulationRun(@PathVariable Long id, Model model) {
        model.addAttribute("simulation", simulationService.getSimulation(id));
        return "simulation/run";
    }
}

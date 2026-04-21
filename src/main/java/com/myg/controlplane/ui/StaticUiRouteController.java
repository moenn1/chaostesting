package com.myg.controlplane.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StaticUiRouteController {

    // Serve stable pretty URLs for the static shell entrypoints.
    @GetMapping({"/experiments", "/experiments/"})
    public String experiments() {
        return "forward:/experiments/index.html";
    }

    @GetMapping({"/live-runs", "/live-runs/"})
    public String liveRuns() {
        return "forward:/live-runs/index.html";
    }

    @GetMapping({"/results", "/results/"})
    public String results() {
        return "forward:/results/index.html";
    }

    @GetMapping({"/history", "/history/"})
    public String history() {
        return "forward:/history/index.html";
    }

    @GetMapping({"/fleet", "/fleet/"})
    public String fleet() {
        return "forward:/fleet/index.html";
    }
}

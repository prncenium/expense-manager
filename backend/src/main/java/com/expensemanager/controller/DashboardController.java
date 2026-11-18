package com.expensemanager.controller;

import com.expensemanager.dto.DashboardResponse;
import com.expensemanager.dto.UploadResult;
import com.expensemanager.service.DashboardService;
import com.expensemanager.service.SampleDataService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;
    private final SampleDataService sampleDataService;

    public DashboardController(DashboardService dashboardService, SampleDataService sampleDataService) {
        this.dashboardService = dashboardService;
        this.sampleDataService = sampleDataService;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@RequestParam(required = false) String month) {
        return dashboardService.get(month);
    }

    /** Loads the bundled sample CSV; only allowed when there are no expenses (else 409). */
    @PostMapping("/sample-data")
    public UploadResult loadSampleData() {
        return sampleDataService.load();
    }
}

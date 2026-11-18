package com.expensemanager.controller;

import com.expensemanager.dto.CompareResponse;
import com.expensemanager.service.CompareService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CompareController {

    private final CompareService compareService;

    public CompareController(CompareService compareService) {
        this.compareService = compareService;
    }

    /** Spend per category in period A vs period B; all dates YYYY-MM-DD, inclusive. */
    @GetMapping("/compare")
    public CompareResponse compare(@RequestParam(required = false) String aFrom,
                                   @RequestParam(required = false) String aTo,
                                   @RequestParam(required = false) String bFrom,
                                   @RequestParam(required = false) String bTo) {
        return compareService.compare(aFrom, aTo, bFrom, bTo);
    }
}

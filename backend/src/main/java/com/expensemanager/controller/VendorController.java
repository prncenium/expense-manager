package com.expensemanager.controller;

import com.expensemanager.dto.UncategorizedVendor;
import com.expensemanager.service.VendorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class VendorController {

    private final VendorService vendorService;

    public VendorController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    @GetMapping("/vendors/uncategorized")
    public List<UncategorizedVendor> uncategorized() {
        return vendorService.uncategorized();
    }

    @GetMapping("/labels")
    public List<String> labels() {
        return vendorService.labels();
    }
}

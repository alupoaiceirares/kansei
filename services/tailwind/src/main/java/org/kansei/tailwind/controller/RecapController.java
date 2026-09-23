package org.kansei.tailwind.controller;

import org.kansei.tailwind.dto.YearlyRecapResponse;
import org.kansei.tailwind.service.RecapService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The caller's own yearly recap, the same numbers the January email carries.
 */
@RestController
@RequestMapping("/tailwind/recaps")
public class RecapController {

    private final RecapService recapService;

    public RecapController(RecapService recapService) {
        this.recapService = recapService;
    }

    @GetMapping
    public List<Integer> years(@RequestHeader("X-User-Id") UUID userId) {
        return recapService.years(userId);
    }

    @GetMapping("/{year}")
    public YearlyRecapResponse recap(@RequestHeader("X-User-Id") UUID userId, @PathVariable int year) {
        return recapService.recap(userId, year);
    }
}

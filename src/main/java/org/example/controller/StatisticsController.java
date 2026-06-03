package org.example.controller;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Exposes the monthly statistics report for restaurant managers. */
@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final org.example.service.StatisticsService statisticsService;

    public StatisticsController(org.example.service.StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /** Returns the current-month statistics summary for a restaurant. */
    @GetMapping("/restaurant/{restaurantId}/monthly")
    public Map<String, Object> monthlySummary(@PathVariable int restaurantId) {
        return statisticsService.getMonthlySummary(restaurantId);
    }
}

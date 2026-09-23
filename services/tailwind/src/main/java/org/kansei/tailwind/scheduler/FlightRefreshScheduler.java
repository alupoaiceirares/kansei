package org.kansei.tailwind.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.kansei.tailwind.service.FlightRefreshService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily pass over flights that were added before they flew, so stats get the real times and aircraft even
 * when nobody opens the flight again.
 */
@Slf4j
@Component
public class FlightRefreshScheduler {

    private final FlightRefreshService flightRefreshService;

    public FlightRefreshScheduler(FlightRefreshService flightRefreshService) {
        this.flightRefreshService = flightRefreshService;
    }

    @Scheduled(cron = "${tailwind.flight-refresh.cron}")
    public void refresh() {
        try {
            flightRefreshService.refreshLanded();
        } catch (RuntimeException ex) {
            log.error("flight refresh failed", ex);
        }
    }
}

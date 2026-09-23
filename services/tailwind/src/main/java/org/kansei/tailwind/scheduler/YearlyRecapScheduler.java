package org.kansei.tailwind.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.kansei.tailwind.service.YearlyRecapMailer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily through January, each run only sends what is still due, so a failed day is caught up the next.
 */
@Slf4j
@Component
public class YearlyRecapScheduler {

    private final YearlyRecapMailer yearlyRecapMailer;

    public YearlyRecapScheduler(YearlyRecapMailer yearlyRecapMailer) {
        this.yearlyRecapMailer = yearlyRecapMailer;
    }

    @Scheduled(cron = "${tailwind.yearly-recap.cron}")
    public void send() {
        try {
            yearlyRecapMailer.sendLastYear();
        } catch (RuntimeException ex) {
            log.error("yearly recap run failed", ex);
        }
    }
}

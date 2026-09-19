package org.kansei.tailwind.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.kansei.tailwind.service.TailwindUserService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Shieldwall hard-deletes purged accounts and tells nobody, so this daily job asks shieldwall which
 * of our users still exist and removes the rest.
 */
@Slf4j
@Component
public class AccountReconcileScheduler {

    private final TailwindUserService tailwindUserService;

    public AccountReconcileScheduler(TailwindUserService tailwindUserService) {
        this.tailwindUserService = tailwindUserService;
    }

    @Scheduled(cron = "${tailwind.account-reconcile.cron}")
    public void reconcile() {
        try {
            tailwindUserService.purgeOrphanedUsers();
        } catch (RuntimeException ex) {
            log.error("account reconcile failed", ex);
        }
    }
}

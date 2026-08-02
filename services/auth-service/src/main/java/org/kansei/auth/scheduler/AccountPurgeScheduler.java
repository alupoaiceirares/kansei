package org.kansei.auth.scheduler;

import org.kansei.auth.service.UserService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AccountPurgeScheduler {

    private final UserService userService;

    public AccountPurgeScheduler(UserService userService) {
        this.userService = userService;
    }

    @Scheduled(cron = "${account.purge.cron:0 0 3 * * *}")
    public void purgeExpiredAccounts() {
        userService.purgeExpiredDeactivatedAccounts();
    }
}

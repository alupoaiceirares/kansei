package org.kansei.auth.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kansei.auth.service.UserService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class AccountPurgeSchedulerTest {

    @Mock
    private UserService userService;

    @Test
    void purgeExpiredAccounts_delegatesToUserService() {
        AccountPurgeScheduler scheduler = new AccountPurgeScheduler(userService);

        scheduler.purgeExpiredAccounts();

        verify(userService).purgeExpiredDeactivatedAccounts();
        verifyNoMoreInteractions(userService);
    }
}

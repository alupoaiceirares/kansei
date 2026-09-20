package org.kansei.tailwind.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kansei.tailwind.client.ShieldwallUserClient;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TailwindUserServiceTest {

    @Mock
    private TailwindUserRepository tailwindUserRepository;

    @Mock
    private ShieldwallUserClient shieldwallUserClient;

    @Mock
    private UserDataCleaner userDataCleaner;

    private TailwindUserService service;

    @BeforeEach
    void setUp() {
        service = new TailwindUserService(tailwindUserRepository, shieldwallUserClient, userDataCleaner);
    }

    private static List<UUID> ids(int count) {
        return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
    }

    @Test
    void purgeDeletesOnlyUsersShieldwallNoLongerHas() {
        List<UUID> all = ids(10);
        List<UUID> gone = List.of(all.get(2), all.get(7));
        when(tailwindUserRepository.findAllUserIds()).thenReturn(all);
        when(shieldwallUserClient.findExistingUserIds(anyCollection()))
                .thenReturn(all.stream().filter(id -> !gone.contains(id)).collect(java.util.stream.Collectors.toSet()));

        int deleted = service.purgeOrphanedUsers();

        assertThat(deleted).isEqualTo(2);
        verify(userDataCleaner).deleteAllFor(gone);
    }

    @Test
    void purgeDeletesNothingWhenShieldwallCallFails() {
        when(tailwindUserRepository.findAllUserIds()).thenReturn(ids(10));
        when(shieldwallUserClient.findExistingUserIds(anyCollection())).thenThrow(new IllegalStateException("shieldwall down"));

        int deleted = service.purgeOrphanedUsers();

        assertThat(deleted).isZero();
        verify(userDataCleaner, never()).deleteAllFor(anyCollection());
    }

    @Test
    void purgeSkipsChunkWhenShieldwallKnowsNoneOfIt() {
        when(tailwindUserRepository.findAllUserIds()).thenReturn(ids(10));
        when(shieldwallUserClient.findExistingUserIds(anyCollection())).thenReturn(Set.of());

        int deleted = service.purgeOrphanedUsers();

        assertThat(deleted).isZero();
        verify(userDataCleaner, never()).deleteAllFor(anyCollection());
    }

    @Test
    void purgeDeletesTheLoneOrphanOfATinyChunk() {
        List<UUID> all = ids(1);
        when(tailwindUserRepository.findAllUserIds()).thenReturn(all);
        when(shieldwallUserClient.findExistingUserIds(anyCollection())).thenReturn(Set.of());

        int deleted = service.purgeOrphanedUsers();

        assertThat(deleted).isEqualTo(1);
        verify(userDataCleaner).deleteAllFor(all);
    }
}

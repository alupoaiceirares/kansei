package org.kansei.tailwind.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kansei.tailwind.client.ShieldwallUserClient;
import org.kansei.tailwind.dto.TailwindUserResponse;
import org.kansei.tailwind.dto.UpdateTailwindUserRequest;
import org.kansei.tailwind.model.TailwindUser;
import org.kansei.tailwind.model.Visibility;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Mock
    private MailEventPublisher mailEventPublisher;

    private TailwindUserService service;

    @BeforeEach
    void setUp() {
        service = new TailwindUserService(tailwindUserRepository, shieldwallUserClient, userDataCleaner, mailEventPublisher);
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
    void deletingYourOwnLogRemovesEverythingYouOwn() {
        UUID user = UUID.randomUUID();
        when(tailwindUserRepository.existsById(user)).thenReturn(true);

        service.deleteOwnLog(user);

        verify(userDataCleaner).deleteAllFor(List.of(user));
    }

    @Test
    void deletingALogThatWasNeverStartedIsNotFound() {
        UUID user = UUID.randomUUID();
        when(tailwindUserRepository.existsById(user)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteOwnLog(user))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(userDataCleaner, never()).deleteAllFor(anyCollection());
    }

    @Test
    void theDefaultVisibilityIsSavedOnTheUser() {
        UUID user = UUID.randomUUID();
        TailwindUser row = TailwindUser.builder().userId(user).joinedAt(Instant.now()).build();
        when(tailwindUserRepository.findById(user)).thenReturn(Optional.of(row));
        when(tailwindUserRepository.save(row)).thenReturn(row);
        when(shieldwallUserClient.resolveUsernames(List.of(user))).thenReturn(Map.of(user, "alexm"));

        TailwindUserResponse response = service.update(user, new UpdateTailwindUserRequest(Visibility.PUBLIC, null), "USER");

        assertThat(row.getDefaultVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(response.defaultVisibility()).isEqualTo(Visibility.PUBLIC);
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

    @Test
    void aDisableRequestMailsTheAdminInboxWithTheUsername() {
        UUID user = UUID.randomUUID();
        when(tailwindUserRepository.existsById(user)).thenReturn(true);
        when(shieldwallUserClient.resolveUsernames(List.of(user))).thenReturn(Map.of(user, "alexm"));

        service.requestDisable(user);

        verify(mailEventPublisher).publishDisableRequest(user, "alexm");
    }

    @Test
    void aDisableRequestNeedsAnOptedInUser() {
        UUID user = UUID.randomUUID();
        when(tailwindUserRepository.existsById(user)).thenReturn(false);

        assertThatThrownBy(() -> service.requestDisable(user)).isInstanceOf(ResponseStatusException.class);
        verify(mailEventPublisher, never()).publishDisableRequest(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}

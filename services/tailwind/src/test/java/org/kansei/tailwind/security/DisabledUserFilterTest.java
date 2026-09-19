package org.kansei.tailwind.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kansei.tailwind.model.TailwindUser;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisabledUserFilterTest {

    @Mock
    private TailwindUserRepository tailwindUserRepository;

    private DisabledUserFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new DisabledUserFilter(tailwindUserRepository);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    private TailwindUser user(UUID id, boolean enabled) {
        return TailwindUser.builder().userId(id).joinedAt(Instant.now()).enabled(enabled).build();
    }

    @Test
    void disabledUserGets403AndChainIsNotCalled() throws Exception {
        UUID id = UUID.randomUUID();
        request.addHeader("X-User-Id", id.toString());
        when(tailwindUserRepository.findById(id)).thenReturn(Optional.of(user(id, false)));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void enabledUserPassesThroughExactlyOnce() throws Exception {
        UUID id = UUID.randomUUID();
        request.addHeader("X-User-Id", id.toString());
        when(tailwindUserRepository.findById(id)).thenReturn(Optional.of(user(id, true)));

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void userWhoNeverOptedInPassesThrough() throws Exception {
        UUID id = UUID.randomUUID();
        request.addHeader("X-User-Id", id.toString());
        when(tailwindUserRepository.findById(id)).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void requestWithoutUserIdHeaderPassesThrough() throws Exception {
        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        verify(tailwindUserRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void malformedUserIdPassesThrough() throws Exception {
        request.addHeader("X-User-Id", "not-a-uuid");

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }
}

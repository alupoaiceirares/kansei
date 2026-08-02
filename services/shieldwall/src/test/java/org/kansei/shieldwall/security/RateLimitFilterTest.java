package org.kansei.shieldwall.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private static final String LIMITED_PATH = "/api/auth/register";
    private static final int MAX_REQUESTS = 3;
    private static final long WINDOW_MINUTES = 15;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "maxRequests", MAX_REQUESTS);
        ReflectionTestUtils.setField(filter, "windowMinutes", WINDOW_MINUTES);
    }

    private MockHttpServletRequest requestTo(String path, String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    void nonLimitedPath_alwaysPassesThrough() throws Exception {
        // /api/auth/login IS limited (added alongside register/resend/reset-request) -
        // /api/auth/verify-email (not the /resend variant) genuinely isn't.
        MockHttpServletRequest request = requestTo("/api/auth/verify-email", "10.0.0.1", null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < MAX_REQUESTS + 5; i++) {
            filter.doFilterInternal(request, response, chain);
        }

        verify(chain, times(MAX_REQUESTS + 5)).doFilter(request, response);
    }

    @Test
    void nonPostMethod_passesThroughEvenOnLimitedPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", LIMITED_PATH);
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < MAX_REQUESTS + 5; i++) {
            filter.doFilterInternal(request, response, chain);
        }

        verify(chain, times(MAX_REQUESTS + 5)).doFilter(request, response);
    }

    @Test
    void withinLimit_allRequestsPassThrough() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < MAX_REQUESTS; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(requestTo(LIMITED_PATH, "10.0.0.1", null), response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        verify(chain, times(MAX_REQUESTS)).doFilter(any(), any());
    }

    @Test
    void overLimit_rejectedWith429AndRetryAfter() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < MAX_REQUESTS; i++) {
            filter.doFilterInternal(requestTo(LIMITED_PATH, "10.0.0.2", null), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(requestTo(LIMITED_PATH, "10.0.0.2", null), blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("Too many requests");
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
        assertThat(Long.parseLong(blocked.getHeader("Retry-After"))).isPositive();
        verify(chain, times(MAX_REQUESTS)).doFilter(any(), any());
    }

    @Test
    void loginPath_isRateLimited() throws Exception {
        // Explicit regression guard - login was added alongside register/resend/reset-request
        // specifically to stop unthrottled brute-force/credential-stuffing and bcrypt-cost abuse.
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < MAX_REQUESTS; i++) {
            filter.doFilterInternal(requestTo("/api/auth/login", "10.0.0.9", null), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(requestTo("/api/auth/login", "10.0.0.9", null), blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    @Test
    void differentIps_trackedIndependently() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < MAX_REQUESTS; i++) {
            filter.doFilterInternal(requestTo(LIMITED_PATH, "10.0.0.3", null), new MockHttpServletResponse(), chain);
        }
        // IP A now exhausted - IP B should be unaffected.
        MockHttpServletResponse responseB = new MockHttpServletResponse();
        filter.doFilterInternal(requestTo(LIMITED_PATH, "10.0.0.4", null), responseB, chain);

        assertThat(responseB.getStatus()).isEqualTo(200);
    }

    @Test
    void differentPaths_trackedIndependentlyForSameIp() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < MAX_REQUESTS; i++) {
            filter.doFilterInternal(requestTo("/api/auth/register", "10.0.0.5", null), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(requestTo("/api/auth/password-reset/request", "10.0.0.5", null), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void xForwardedFor_usedInPlaceOfRemoteAddr() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        // Same X-Forwarded-For, different remote addr (e.g. both behind the same gateway) - should share one bucket
        for (int i = 0; i < MAX_REQUESTS; i++) {
            filter.doFilterInternal(requestTo(LIMITED_PATH, "172.17.0.1", "203.0.113.9"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(requestTo(LIMITED_PATH, "172.17.0.2", "203.0.113.9"), blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    @Test
    void expiredWindow_resetsAndAllowsRequestsAgain() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < MAX_REQUESTS; i++) {
            filter.doFilterInternal(requestTo(LIMITED_PATH, "10.0.0.6", null), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(requestTo(LIMITED_PATH, "10.0.0.6", null), blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);

        backdateAllWindows(filter, WINDOW_MINUTES + 1);

        MockHttpServletResponse afterReset = new MockHttpServletResponse();
        filter.doFilterInternal(requestTo(LIMITED_PATH, "10.0.0.6", null), afterReset, chain);
        assertThat(afterReset.getStatus()).isEqualTo(200);
    }

    @Test
    void cleanup_evictsExpiredWindowsOnly() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        filter.doFilterInternal(requestTo(LIMITED_PATH, "10.0.0.7", null), new MockHttpServletResponse(), chain);
        filter.doFilterInternal(requestTo("/api/auth/password-reset/request", "10.0.0.8", null), new MockHttpServletResponse(), chain);

        backdateAllWindows(filter, WINDOW_MINUTES + 1);
        filter.cleanup();

        @SuppressWarnings("unchecked")
        Map<String, Object> windows = (Map<String, Object>) getWindowsMap(filter);
        assertThat(windows).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static void backdateAllWindows(RateLimitFilter filter, long minutesAgo) throws Exception {
        Map<String, Object> windows = (Map<String, Object>) getWindowsMap(filter);
        for (Object window : windows.values()) {
            Field startField = window.getClass().getDeclaredField("start");
            startField.setAccessible(true);
            startField.set(window, Instant.now().minusSeconds(minutesAgo * 60));
        }
    }

    private static Object getWindowsMap(RateLimitFilter filter) throws Exception {
        Field windowsField = RateLimitFilter.class.getDeclaredField("windows");
        windowsField.setAccessible(true);
        Object value = windowsField.get(filter);
        return value != null ? value : new ConcurrentHashMap<>();
    }
}

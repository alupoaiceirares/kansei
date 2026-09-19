package org.kansei.tailwind.service;

import org.kansei.tailwind.repository.TailwindUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Admin gate called at the top of each admin action. The role comes from the X-User-Role header
 * control-tower injects from the verified JWT, tailwind has no role column, but the caller must
 * still have a tailwind_users row, same as wirehood.
 */
@Service
public class AdminAuthService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final TailwindUserRepository tailwindUserRepository;

    public AdminAuthService(TailwindUserRepository tailwindUserRepository) {
        this.tailwindUserRepository = tailwindUserRepository;
    }

    public void requireAdmin(UUID userId, String role) {
        if (!tailwindUserRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a tailwind user");
        }
        if (!ADMIN_ROLE.equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }
    }
}

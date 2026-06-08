package com.property.repair.security;

import com.property.repair.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility to access the current authenticated user.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Get the currently authenticated user.
     */
    public static SecurityUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException(401, "User not authenticated");
        }
        if (auth.getPrincipal() instanceof SecurityUser securityUser) {
            return securityUser;
        }
        throw new BusinessException(401, "Invalid authentication context");
    }

    /**
     * Get the current user's ID.
     */
    public static Long getCurrentUserId() {
        return getCurrentUser().getUserId();
    }

    /**
     * Get the current user's role.
     */
    public static String getCurrentRole() {
        return getCurrentUser().getRole();
    }

    /**
     * Check if the current user has a specific role.
     */
    public static boolean hasRole(String role) {
        return getCurrentUser().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    /**
     * Check if the current user is an admin.
     */
    public static boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * Check if the current user is a supervisor or admin.
     */
    public static boolean isSupervisorOrAbove() {
        return hasRole("SUPERVISOR") || hasRole("ADMIN");
    }
}

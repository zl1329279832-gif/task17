package com.property.repair.security;

import com.property.repair.common.enums.RoleType;
import com.property.repair.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User is not authenticated");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser) {
            return (LoginUser) principal;
        }
        throw new UnauthorizedException("Invalid authentication principal");
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().getUserId();
    }

    public static RoleType getCurrentRole() {
        return getCurrentUser().getRole();
    }

    public static String getCurrentUsername() {
        return getCurrentUser().getUsername();
    }

    public static Long getCurrentCommunityId() {
        return getCurrentUser().getCommunityId();
    }
}

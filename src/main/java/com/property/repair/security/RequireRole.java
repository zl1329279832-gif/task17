package com.property.repair.security;

import java.lang.annotation.*;

/**
 * Annotation to restrict method access to specific roles.
 * Used on service methods for fine-grained access control.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /**
     * Allowed roles (e.g., "OWNER", "WORKER", "SUPERVISOR", "ADMIN").
     */
    String[] value();

    /**
     * Error message when access is denied.
     */
    String message() default "Insufficient permissions";
}

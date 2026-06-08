package com.property.repair.common.constants;

public class RedisKeyConstants {

    private RedisKeyConstants() {
    }

    public static final String JWT_BLACKLIST = "jwt:blacklist:";
    public static final String IDEMPOTENT_KEY = "idempotent:";
    public static final String SCHEDULER_LOCK = "scheduler:lock:";
    public static final String USER_TOKEN = "user:token:";
}

package com.ticketshop.admin.common.api;

import java.util.Set;

public final class ApiValues {
    private ApiValues() {}
    public static String id(Long value) {
        if (value == null || value <= 0) throw new IllegalArgumentException("Positive signed-64-bit id required");
        return value.toString();
    }
    public static long parseId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Invalid id");
        return Long.parseLong(value);
    }
    public static void requirePage(int page, int pageSize) {
        if (page < 1 || !Set.of(10,20,50,100).contains(pageSize)) throw new IllegalArgumentException("Invalid page");
    }
    public static void requireChanged(int affectedRows) {
        if (affectedRows != 1) throw new VersionConflictException();
    }
    public static final class VersionConflictException extends RuntimeException {
        public VersionConflictException() { super("VERSION_CONFLICT"); }
        public int httpStatus() { return 409; }
    }
}

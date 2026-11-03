package com.ticketshop.admin.customer.api;

import java.time.Instant;
import com.ticketshop.admin.common.api.ApiValues;
import com.ticketshop.admin.customer.infrastructure.CustomerAccountEntity;

/** Explicit allowlist: no password hash, token digest, internal mutation state or arbitrary entity serialization. */
public record CustomerView(String id, String username, String displayName, String status,
        String termStatus, Instant validFrom, Instant validUntil, boolean mustChangePassword,
        String remark, Instant lastLoginAt, Instant lastValidatedAt, String version, Instant createdAt, Instant updatedAt) {
    public static CustomerView from(CustomerAccountEntity value, Instant now) {
        String term = now.isBefore(value.getValidFrom()) ? "NOT_STARTED"
                : now.isBefore(value.getValidUntil()) ? "IN_TERM" : "EXPIRED";
        return new CustomerView(ApiValues.id(value.getId()), value.getUsername(), value.getDisplayName(), value.getStatus(),
                term, value.getValidFrom(), value.getValidUntil(), Boolean.TRUE.equals(value.getMustChangePassword()),
                value.getRemark(), value.getLastLoginAt(), value.getLastValidatedAt(), value.getVersion().toString(), value.getCreatedAt(), value.getUpdatedAt());
    }
}

package com.ticketshop.admin.customer.infrastructure;

import java.time.Instant;
import java.util.Set;

/** Only whitelisted sorting reaches XML; all text/time values are bound parameters. */
public record CustomerFilter(String keyword, String status, String termStatus, Instant validUntilFrom,
                             Instant validUntilTo, Instant now, Sort sort) {
    public enum Sort { CREATED_AT_ASC, CREATED_AT_DESC, VALID_UNTIL_ASC, VALID_UNTIL_DESC, LAST_LOGIN_AT_ASC, LAST_LOGIN_AT_DESC }
    public CustomerFilter {
        if (now == null || sort == null) throw new IllegalArgumentException("now and sort are required");
        if (status != null && !Set.of("ACTIVE", "FROZEN", "DISABLED").contains(status)) throw new IllegalArgumentException("Invalid status");
        if (termStatus != null && !Set.of("NOT_STARTED", "IN_TERM", "EXPIRED").contains(termStatus)) throw new IllegalArgumentException("Invalid term status");
        if (validUntilFrom != null && validUntilTo != null && !validUntilFrom.isBefore(validUntilTo)) throw new IllegalArgumentException("Invalid time range");
        if (keyword != null && keyword.length() > 64) throw new IllegalArgumentException("Keyword too long");
    }
}

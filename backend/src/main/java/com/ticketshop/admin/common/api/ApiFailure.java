package com.ticketshop.admin.common.api;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiFailure(Problem error, String requestId, Instant serverNow) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Problem(ErrorCode code, String message, Details details) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Details(List<String> denialReasons, String status, Instant validFrom, Instant validUntil,
            String currentVersion, List<FieldError> fieldErrors, Integer retryAfterSeconds, Integer minimumProtocolVersion) {}
    public record FieldError(String field, String reason) {}
}

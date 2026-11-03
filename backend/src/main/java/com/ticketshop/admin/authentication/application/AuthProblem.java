package com.ticketshop.admin.authentication.application;

import com.ticketshop.admin.common.api.*;
import java.util.List;

public final class AuthProblem extends RuntimeException {
    private final ErrorCode code;
    private final ApiFailure.Details details;
    public AuthProblem(ErrorCode code) { this(code, null); }
    public AuthProblem(ErrorCode code, ApiFailure.Details details) { super(code.name()); this.code=code; this.details=details; }
    public ErrorCode code() { return code; }
    public ApiFailure.Details details() { return details; }
    public static AuthProblem denied(List<String> reasons, String status, java.time.Instant from, java.time.Instant until) {
        return new AuthProblem(ErrorCode.valueOf(reasons.getFirst()),new ApiFailure.Details(reasons,status,from,until,null,null,null,null));
    }
    public static AuthProblem limited(int seconds) { return new AuthProblem(ErrorCode.LOGIN_RATE_LIMITED,
        new ApiFailure.Details(null,null,null,null,null,null,Math.max(1,seconds),null)); }
}

package com.ticketshop.admin.common.api;

import java.time.Instant;

/** HTTP payloads use explicit DTOs. Persistence entities must never be supplied as T. */
public record ApiEnvelope<T>(T data, String requestId, Instant serverNow) {}

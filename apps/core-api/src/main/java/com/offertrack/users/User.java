package com.offertrack.users;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.lang.Nullable;

public record User(
    UUID id,
    String email,
    @Nullable String passwordHash,
    String name,
    OffsetDateTime emailVerifiedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

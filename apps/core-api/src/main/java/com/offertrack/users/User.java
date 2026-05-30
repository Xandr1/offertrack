package com.offertrack.users;

import java.time.OffsetDateTime;
import java.util.UUID;

public record User(
    UUID id,
    String email,
    String passwordHash,
    String name,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

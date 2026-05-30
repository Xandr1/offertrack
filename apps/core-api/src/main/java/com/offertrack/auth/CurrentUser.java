package com.offertrack.auth;

import java.util.UUID;

public record CurrentUser(UUID id, String email) {}

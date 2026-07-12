package com.offertrack.auth.dto;

public record CsrfTokenResponse(String token, String headerName) {}

package com.bizsage.api.auth;

public record JwtPrincipal(String username, Role role) {
}

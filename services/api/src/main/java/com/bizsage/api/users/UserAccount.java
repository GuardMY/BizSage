package com.bizsage.api.users;

import com.bizsage.api.auth.Role;

public record UserAccount(
    long id,
    String username,
    String password,
    Role role,
    String phone,
    String identity,
    String regionId,
    String industryId) {
}

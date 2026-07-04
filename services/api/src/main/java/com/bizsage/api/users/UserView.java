package com.bizsage.api.users;

import com.bizsage.api.auth.Role;

public record UserView(
    long id,
    String username,
    Role role,
    String phoneMasked,
    String identityMasked,
    String regionId,
    String industryId,
    String membershipLevel,
    String consultationPreferences) {
}

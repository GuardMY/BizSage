package com.bizsage.api.users;

import com.bizsage.api.auth.Role;
import com.bizsage.api.privacy.PrivacyService;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class UserStore {
  private final PrivacyService privacyService;
  private final JdbcTemplate jdbcTemplate;

  public UserStore(PrivacyService privacyService, JdbcTemplate jdbcTemplate) {
    this.privacyService = privacyService;
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<UserAccount> findByUsername(String username) {
    List<UserAccount> matches = jdbcTemplate.query("""
        select id, username, password_hash, role, phone_encrypted, identity_encrypted,
               region_id, industry_id, membership_level, consultation_preferences
          from users
         where username = ?
        """, mapper(), username);
    return matches.stream().findFirst();
  }

  public List<UserView> listViews() {
    return jdbcTemplate.query("""
        select id, username, password_hash, role, phone_encrypted, identity_encrypted,
               region_id, industry_id, membership_level, consultation_preferences
          from users
         order by id
        """, mapper()).stream().map(this::toView).toList();
  }

  public UserView toView(UserAccount account) {
    return new UserView(
        account.id(),
        account.username(),
        account.role(),
        privacyService.maskPhone(account.phone()),
        privacyService.maskIdentity(account.identity()),
        account.regionId(),
        account.industryId(),
        account.membershipLevel(),
        account.consultationPreferences());
  }

  private RowMapper<UserAccount> mapper() {
    return (rs, rowNum) -> new UserAccount(
        rs.getLong("id"),
        rs.getString("username"),
        rs.getString("password_hash"),
        Role.valueOf(rs.getString("role")),
        rs.getString("phone_encrypted"),
        rs.getString("identity_encrypted"),
        rs.getString("region_id"),
        rs.getString("industry_id"),
        rs.getString("membership_level"),
        rs.getString("consultation_preferences"));
  }
}

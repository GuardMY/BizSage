package com.bizsage.api.auth;

/**
 * BizSage role hierarchy.
 *
 * <p>Admin roles (SUPER_ADMIN, OPERATOR) have unrestricted data access.
 * End-user roles (SEED_PAID, INTERNAL, FREE) are scoped by region/industry
 * and membership level. LEGAL_FREEZE entirely blocks user-facing access.
 */
public enum Role {
  SUPER_ADMIN,
  OPERATOR,
  SEED_PAID,
  INTERNAL,
  FREE,
  LEGAL_FREEZE,
  USER  // Legacy — mapped to FREE at runtime
}

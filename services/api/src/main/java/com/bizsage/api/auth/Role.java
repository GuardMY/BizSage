package com.bizsage.api.auth;

/**
 * BizSage role hierarchy.
 *
 * <p>End-user roles are scoped by region/industry and membership level.
 * LEGAL_FREEZE entirely blocks user-facing access.
 */
public enum Role {
  SEED_PAID,
  INTERNAL,
  FREE,
  LEGAL_FREEZE,
  USER  // Legacy — mapped to FREE at runtime
}

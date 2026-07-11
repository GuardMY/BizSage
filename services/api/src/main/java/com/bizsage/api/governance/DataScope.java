package com.bizsage.api.governance;

/**
 * Resolved data-permission scope for a user request.
 *
 * <ul>
 *   <li>paid user (SEED_PAID): scoped to their own region + industry, can see PAID content.</li>
 *   <li>internal user (INTERNAL): scoped to their own region + industry, can see PAID content.</li>
 *   <li>free user (FREE): scoped to their own region + industry, FREE content only.</li>
 * </ul>
 */
public record DataScope(
    String regionId,
    String industryId,
    String membershipLevel,
    boolean requireAuditLog
) {

  public boolean isAdmin() {
    return false;
  }

  /** Returns true when the scope allows access to paid/entitlement-gated content. */
  public boolean canAccessPaid() {
    return "SEED_PAID".equals(membershipLevel)
        || "INTERNAL".equals(membershipLevel)
        || isAdmin();
  }

  /** Returns true when the given target region is within this scope. */
  public boolean includesRegion(String targetRegion) {
    return regionId == null || regionId.equals(targetRegion);
  }

  /** Returns true when the given target industry is within this scope. */
  public boolean includesIndustry(String targetIndustry) {
    return industryId == null || industryId.equals(targetIndustry);
  }

  public static DataScope unrestricted() {
    return new DataScope("cn-default", "general", "INTERNAL", false);
  }

  /** V2: Returns a scope that blocks all data access (LEGAL_FREEZE). */
  public static DataScope blocked() {
    return new DataScope("__blocked__", "__blocked__", "LEGAL_FREEZE", true);
  }

  /** V2: Returns true when this scope blocks all access. */
  public boolean isBlocked() {
    return "__blocked__".equals(regionId) && "__blocked__".equals(industryId);
  }
}

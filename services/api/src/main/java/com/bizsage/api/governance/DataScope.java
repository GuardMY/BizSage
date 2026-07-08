package com.bizsage.api.governance;

/**
 * Resolved data-permission scope for a user request.
 *
 * <ul>
 *   <li>admin (SUPER_ADMIN/OPERATOR): regionId and industryId are null (unrestricted).</li>
 *   <li>paid user (SEED_PAID): scoped to their own region + industry, can see PAID content.</li>
 *   <li>free user (FREE): scoped to their own region + industry, FREE content only.</li>
 * </ul>
 */
public record DataScope(
    String regionId,           // null = all regions (admin)
    String industryId,         // null = all industries (admin)
    String membershipLevel,    // FREE, SEED_PAID, INTERNAL
    boolean requireAuditLog    // true when admin accesses outside their default scope
) {

  public boolean isAdmin() {
    return regionId == null && industryId == null;
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
    return new DataScope(null, null, "INTERNAL", false);
  }
}

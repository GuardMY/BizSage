# Change Log

## 2026-07-04

### Product Milestone Roadmap

- Change type: documentation maintenance.
- Affected modules: milestone documentation and product planning.
- Main changes:
  - Added the cross-version product milestone roadmap in `docs/milestones/product-milestones.md`.
  - Added the matching Chinese version in `docs/milestones/product-milestones-zh-CN.md`.
  - Defined V1 as the active implementation version and V2/V3/V4 as planning targets only.
- Verification results:
  - Markdown bilingual pair check returned `NO_MISSING_PAIRS`.
  - Old naming and stale reference scan returned no matches.
- Unfinished items:
  - Detailed V2/V3/V4 milestone documents are not created yet and must be approved before implementation starts.

## 2026-07-04

### Bilingual Documentation Backfill

- Change type: documentation maintenance.
- Affected modules: root documentation, Android docs, API docs, database docs, deployment docs, milestone docs, acceptance docs, and standards docs.
- Main changes:
  - Standardized bilingual naming: English uses default `*.md`; Chinese uses `*-zh-CN.md`.
  - Renamed existing bilingual files to the new convention, including `AGENTS.md`, `AGENTS-zh-CN.md`, `CHANGELOG.md`, and `CHANGELOG-zh-CN.md`.
  - Added Chinese versions for historical single-language documents.
  - Updated agent development standards and root `AGENTS.md` with the new naming convention.
  - Updated the bilingual documentation audit to show no remaining Markdown documentation gaps, excluding generated/cache files.
- Verification results:
  - Checked Markdown files outside dependency, build, and cache directories.
  - Confirmed each English `*.md` documentation file has a matching `*-zh-CN.md` file where required.
- Unfinished items:
  - None for Markdown documentation currently in scope.

## 2026-07-04

### Documentation Standards

- Change type: documentation and process standards.
- Affected modules: root-level agent standards, documentation standards, change log, and milestone maintenance process.
- Main changes:
  - Added root-level `AGENTS.md` as the primary entry point for agent development standards.
  - Added the matching English root version `AGENTS.md`.
  - Added the requirement that every functional change must update both `CHANGELOG.md` and `CHANGELOG-zh-CN.md`.
  - Added the requirement that every milestone progress update must update the milestone documents.
  - Added a bilingual documentation maintenance audit report.
- Verification results:
  - Confirmed that the key bilingual rules exist in both Chinese and English standards.
  - Generated the current list of documents missing bilingual maintenance.
- Unfinished items:
  - Superseded by the bilingual documentation backfill above. See `docs/standards/documentation-bilingual-audit.md`.

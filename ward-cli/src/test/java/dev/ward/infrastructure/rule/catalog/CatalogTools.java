package dev.ward.infrastructure.rule.catalog;

import java.util.List;

/** The tools whose rules the catalog maps, in the order the catalog files list them. */
public final class CatalogTools {

  /** Every tool of roadmaps #306 and #348, by the key a catalog row uses for its counterparts. */
  public static final List<String> ALL =
      List.of(
          "checkstyle",
          "pmd",
          "spotbugs",
          "errorprone",
          "nullaway",
          "semgrep",
          "eslint",
          "typescript-eslint",
          "angular-eslint",
          "biome",
          "ruff",
          "pylint",
          "swiftlint",
          "detekt",
          "ktlint",
          "android-lint",
          "knip",
          "vulture",
          "periphery",
          "cpd",
          "jscpd",
          "gitleaks",
          "spectral",
          "oasdiff");

  private CatalogTools() {}
}

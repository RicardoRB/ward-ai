package dev.ward.infrastructure.init;

import dev.ward.domain.init.GradleToolCatalog;
import dev.ward.domain.init.QualityPreset;
import java.util.List;
import java.util.Set;

/**
 * The blocks init appends after the {@code plugins {}} block, in order. A block is added when its
 * gate is selected, its preset requirement is met, and the script does not already contain its
 * marker text, so running init again adds nothing.
 */
final class GradleBuildBlocks {
  static final String MARKER = "// Ward-managed quality gate configuration";

  private static final List<Block> BLOCKS =
      List.of(
          new Block(
              "enforcer",
              false,
              "checkstyleMain",
              "gradle-enforcer",
              "",
              (kotlin, preset, versions) -> GradleQualityBlocks.enforcement(kotlin)),
          new Block(
              "archunit",
              false,
              "archunit-junit5",
              "archunit-junit5",
              GradleToolCatalog.ARCHUNIT,
              (kotlin, preset, versions) ->
                  GradleQualityBlocks.archunit(
                      kotlin, versions.version(GradleToolCatalog.ARCHUNIT))),
          new Block(
              "dependency-check",
              false,
              "NVD_API_KEY",
              "dependency-check-nvd-key",
              "",
              (kotlin, preset, versions) -> GradleQualityBlocks.dependencyCheck(kotlin)),
          new Block(
              "checkstyle",
              true,
              GradleQualityBlocks.CHECKSTYLE_CONFIG,
              "checkstyle-config",
              "",
              (kotlin, preset, versions) ->
                  GradleQualityBlocks.checkstyle(
                      kotlin, versions.version(GradleToolCatalog.CHECKSTYLE_ENGINE))),
          new Block(
              "pmd",
              true,
              GradleQualityBlocks.PMD_RULESET,
              "pmd-ruleset",
              "",
              (kotlin, preset, versions) ->
                  GradleQualityBlocks.pmd(kotlin, versions.version(GradleToolCatalog.PMD_ENGINE))),
          new Block(
              "format",
              false,
              "spotless {",
              "spotless-google-java-format",
              "",
              (kotlin, preset, versions) ->
                  GradleQualityBlocks.spotless(
                      kotlin, versions.version(GradleToolCatalog.FORMATTER))),
          new Block(
              "spotbugs",
              false,
              "spotbugs {",
              "spotbugs-engine",
              "",
              (kotlin, preset, versions) ->
                  GradleEngineBlocks.spotbugs(
                      kotlin, versions.version(GradleToolCatalog.SPOTBUGS_ENGINE))),
          new Block(
              "coverage",
              false,
              GradleQualityBlocks.COVERAGE_TASK,
              "jacoco-coverage-rule",
              "",
              (kotlin, preset, versions) ->
                  GradleQualityBlocks.coverage(
                      kotlin, preset, versions.version(GradleToolCatalog.JACOCO_ENGINE))),
          new Block(
              "mutation",
              false,
              GradleQualityBlocks.PITEST_JUNIT5,
              "pitest-junit5",
              "",
              (kotlin, preset, versions) ->
                  GradleQualityBlocks.pitest(
                      kotlin, preset, versions.version(GradleToolCatalog.PITEST_ENGINE))),
          new Block(
              "license",
              false,
              "licenseReport {",
              "license-policy",
              "",
              (kotlin, preset, versions) -> GradleLicensePolicy.block(kotlin, preset)));

  private GradleBuildBlocks() {}

  static void append(
      final GradleBuildFileEdit edit,
      final Set<String> selected,
      final QualityPreset preset,
      final boolean kotlin,
      final GradleToolVersions versions) {
    for (final Block block : BLOCKS) {
      if (selected.contains(block.gate())
          && (preset != null || !block.needsPreset())
          && !edit.contains(block.present())) {
        edit.append(
            "\n" + MARKER + "\n" + block.body().render(kotlin, preset, versions),
            block.tool(),
            block.versionOf().isEmpty() ? "" : versions.version(block.versionOf()));
      }
    }
  }

  /**
   * {@code present} is text whose presence means the script already has this block, and {@code
   * versionOf} names the catalog tool whose version the block records, or is empty.
   */
  private record Block(
      String gate,
      boolean needsPreset,
      String present,
      String tool,
      String versionOf,
      BlockBody body) {}

  /** Renders a block for the DSL, the preset, and the versions chosen for the project. */
  @FunctionalInterface
  private interface BlockBody {
    String render(boolean kotlin, QualityPreset preset, GradleToolVersions versions);
  }
}

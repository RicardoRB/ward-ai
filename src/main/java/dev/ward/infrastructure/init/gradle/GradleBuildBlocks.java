package dev.ward.infrastructure.init.gradle;

import dev.ward.domain.init.GradleToolCatalog;
import dev.ward.domain.init.QualityPreset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The blocks init appends after the {@code plugins {}} block, in order. A block is added when its
 * gate is selected, its preset requirement is met, and the script does not already contain its
 * marker text, so running init again adds nothing.
 */
public final class GradleBuildBlocks {
  static final String MARKER = "// Ward-managed quality gate configuration";

  private static final List<Block> BLOCKS =
      List.of(
          new Block(
              "enforcer",
              false,
              true,
              "checkstyleMain",
              "gradle-enforcer",
              "",
              (kotlin, preset, versions, module) -> GradleQualityBlocks.enforcement(kotlin)),
          new Block(
              "archunit",
              false,
              false,
              "archunit-junit5",
              "archunit-junit5",
              GradleToolCatalog.ARCHUNIT,
              (kotlin, preset, versions, module) ->
                  GradleQualityBlocks.archunit(
                      kotlin, versions.version(GradleToolCatalog.ARCHUNIT))),
          new Block(
              "dependency-check",
              false,
              false,
              "NVD_API_KEY",
              "dependency-check-nvd-key",
              "",
              (kotlin, preset, versions, module) -> GradleQualityBlocks.dependencyCheck(kotlin)),
          new Block(
              "checkstyle",
              true,
              true,
              GradleQualityBlocks.CHECKSTYLE_CONFIG,
              "checkstyle-config",
              "",
              (kotlin, preset, versions, module) ->
                  GradleQualityBlocks.checkstyle(
                      kotlin, versions.version(GradleToolCatalog.CHECKSTYLE_ENGINE), module)),
          new Block(
              "pmd",
              true,
              true,
              GradleQualityBlocks.PMD_RULESET,
              "pmd-ruleset",
              "",
              (kotlin, preset, versions, module) ->
                  GradleQualityBlocks.pmd(
                      kotlin, versions.version(GradleToolCatalog.PMD_ENGINE), module)),
          new Block(
              "format",
              false,
              true,
              "spotless {",
              "spotless-google-java-format",
              "",
              (kotlin, preset, versions, module) ->
                  GradleQualityBlocks.spotless(
                      kotlin, versions.version(GradleToolCatalog.FORMATTER))),
          new Block(
              "spotbugs",
              false,
              true,
              "spotbugs {",
              "spotbugs-engine",
              "",
              (kotlin, preset, versions, module) ->
                  GradleEngineBlocks.spotbugs(
                      kotlin, versions.version(GradleToolCatalog.SPOTBUGS_ENGINE))),
          new Block(
              "coverage",
              false,
              true,
              GradleQualityBlocks.COVERAGE_TASK,
              "jacoco-coverage-rule",
              "",
              (kotlin, preset, versions, module) ->
                  GradleQualityBlocks.coverage(
                      kotlin, preset, versions.version(GradleToolCatalog.JACOCO_ENGINE))),
          new Block(
              "mutation",
              false,
              true,
              GradleQualityBlocks.PITEST_JUNIT5,
              "pitest-junit5",
              "",
              (kotlin, preset, versions, module) ->
                  GradleQualityBlocks.pitest(
                      kotlin, versions.version(GradleToolCatalog.PITEST_ENGINE))),
          new Block(
              "license",
              false,
              false,
              "licenseReport {",
              "license-policy",
              "",
              (kotlin, preset, versions, module) -> GradleLicensePolicy.block(kotlin, preset)));

  private GradleBuildBlocks() {}

  /** The gates whose blocks go into each Kotlin module script rather than the root script. */
  public static Set<String> moduleGates() {
    return BLOCKS.stream().filter(Block::module).map(Block::gate).collect(Collectors.toSet());
  }

  static void append(
      final GradleBuildFileEdit edit,
      final Set<String> selected,
      final QualityPreset preset,
      final GradleToolVersions versions,
      final boolean module) {
    final boolean kotlin = edit.buildFile().toString().endsWith(".kts");
    for (final Block block : BLOCKS) {
      if (selected.contains(block.gate())
          && (preset != null || !block.needsPreset())
          && !edit.contains(block.present())) {
        edit.append(
            "\n" + MARKER + "\n" + block.body().render(kotlin, preset, versions, module),
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
      boolean module,
      String present,
      String tool,
      String versionOf,
      BlockBody body) {}

  /** Renders a block for the DSL, the preset, and the versions chosen for the project. */
  @FunctionalInterface
  private interface BlockBody {
    String render(
        boolean kotlin, QualityPreset preset, GradleToolVersions versions, boolean module);
  }
}

package dev.ward.infrastructure.init.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.ArchitectureTestChange;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.FileSystemSwiftToolConfigs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Harmonize package and root config Ward writes for each architecture style. */
class HarmonizeRulesGeneratorTest {
  private static final QualityPreset.ArchitectureExtras NO_EXTRAS =
      new QualityPreset.ArchitectureExtras(false, false, false);

  @TempDir Path root;

  @Test
  void hexagonalForbidsDomainAndApplicationFromAdaptersAndInfrastructure() throws IOException {
    final ArchitectureTestChange change = generator().apply(project(), "hexagonal", NO_EXTRAS);

    assertThat(change.created()).isTrue();
    assertThat(Files.readString(change.file()))
        .contains("func domainLayerRespectsRules()", "func applicationLayerRespectsRules()")
        .contains("forbidden: [\"adapter\", \"infrastructure\"]")
        .doesNotContain("controller");
  }

  @Test
  void cleanForbidsOuterLayersFromDomainAndCore() throws IOException {
    final ArchitectureTestChange change = generator().apply(project(), "clean", NO_EXTRAS);

    assertThat(Files.readString(change.file()))
        .contains("func coreLayerRespectsRules()")
        .contains("forbidden: [\"controller\", \"adapter\", \"infrastructure\"]");
  }

  @Test
  void layeredForbidsControllersFromRepositories() throws IOException {
    final ArchitectureTestChange change = generator().apply(project(), "layered", NO_EXTRAS);

    assertThat(Files.readString(change.file()))
        .contains("func controllerLayerRespectsRules()")
        .contains("forbidden: [\"repository\"]");
  }

  @Test
  void writesAnOwnershipMarkedPackagePinningHarmonizeAndARootConfigWithExcludes()
      throws IOException {
    generator().apply(project(), "layered", NO_EXTRAS);

    assertThat(Files.readString(root.resolve(HarmonizeRulesSource.MANIFEST)))
        .startsWith("// swift-tools-version: 5.9\n// managed-by: ward\n")
        .contains("exact: \"1.3.0\"", ".package(")
        .contains("https://github.com/perrystreetsoftware/Harmonize.git");
    assertThat(Files.readString(root.resolve(HarmonizeRulesSource.ROOT_CONFIG)))
        .startsWith("# managed-by: ward\n")
        .contains("  - .ward\n", "  - DerivedData\n");
  }

  @Test
  void keepsAUserRootConfigAndPlansTheExcludeNote() throws IOException {
    Files.writeString(root.resolve(".harmonize.yaml"), "excludes:\n  - Generated\n");

    final PlannedFileChange plan = generator().plan(project(), "layered", NO_EXTRAS);
    generator().apply(project(), "layered", NO_EXTRAS);

    assertThat(plan.details()).anyMatch(note -> note.contains("add .ward to its excludes"));
    assertThat(Files.readString(root.resolve(".harmonize.yaml")))
        .isEqualTo("excludes:\n  - Generated\n");
  }

  @Test
  void rollbackOfAFreshSetupRemovesEverythingItWrote() throws IOException {
    final ArchitectureTestChange change = generator().apply(project(), "hexagonal", NO_EXTRAS);

    generator().rollback(change);

    assertThat(root.resolve(".ward/swift-architecture")).doesNotExist();
    assertThat(root.resolve(".harmonize.yaml")).doesNotExist();
  }

  @Test
  void rollbackOfARewriteRestoresThePreviousRulesExactly() throws IOException {
    generator().apply(project(), "layered", NO_EXTRAS);
    final String before = Files.readString(root.resolve(HarmonizeRulesSource.RULES));
    final ArchitectureTestChange change = generator().apply(project(), "hexagonal", NO_EXTRAS);

    generator().rollback(change);

    assertThat(Files.readString(root.resolve(HarmonizeRulesSource.RULES))).isEqualTo(before);
  }

  @Test
  void rollbackKeepsAUserRootConfig() throws IOException {
    Files.writeString(root.resolve(".harmonize.yaml"), "excludes:\n  - Generated\n");
    final ArchitectureTestChange change = generator().apply(project(), "layered", NO_EXTRAS);

    generator().rollback(change);

    assertThat(root.resolve(".harmonize.yaml")).exists();
    assertThat(root.resolve(".ward/swift-architecture")).doesNotExist();
  }

  private HarmonizeRulesGenerator generator() {
    return new HarmonizeRulesGenerator(new FileSystemSwiftToolConfigs());
  }

  private Project project() {
    return new Project(root, Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);
  }
}

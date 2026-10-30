package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.ArchitectureTestChange;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.FileSystemPythonToolConfigs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The import-linter contracts Ward writes for each architecture style and preset. */
class ImportLinterConfigGeneratorTest {
  private static final QualityPreset.ArchitectureExtras NO_EXTRAS =
      new QualityPreset.ArchitectureExtras(false, false, false);
  private static final QualityPreset.ArchitectureExtras STRICT =
      new QualityPreset.ArchitectureExtras(true, false, false);

  @TempDir Path root;

  @Test
  void layeredForbidsControllersFromImportingRepositories() throws IOException {
    shop("controller", "repository");

    final ArchitectureTestChange change = generator().apply(project(), "layered", NO_EXTRAS);

    assertThat(change.created()).isTrue();
    assertThat(Files.readString(change.file()))
        .contains("root_package = shop")
        .contains(
            "shop.controller\n", "shop.**.controller", "shop.repository\n", "shop.**.repository");
  }

  @Test
  void hexagonalForbidsDomainAndApplicationFromAdapters() throws IOException {
    shop("domain", "application", "adapter", "infrastructure");

    assertThat(Files.readString(generator().apply(project(), "hexagonal", NO_EXTRAS).file()))
        .contains(
            "shop.**.domain", "shop.**.application", "shop.**.adapter", "shop.**.infrastructure");
  }

  @Test
  void cleanForbidsCoreFromOuterLayers() throws IOException {
    shop("core", "controller");

    assertThat(Files.readString(generator().apply(project(), "clean", NO_EXTRAS).file()))
        .contains(
            "shop.**.core", "shop.**.controller", "shop.**.adapter", "shop.**.infrastructure");
  }

  @Test
  void strictAddsIndependenceBetweenFeaturePackages() throws IOException {
    shop("orders", "billing");

    assertThat(Files.readString(generator().apply(project(), "layered", STRICT).file()))
        .contains("type = independence", "shop.billing", "shop.orders");
  }

  @Test
  void standardWithoutFeaturesHasNoIndependenceContract() throws IOException {
    shop("orders", "billing");

    assertThat(Files.readString(generator().apply(project(), "layered", NO_EXTRAS).file()))
        .doesNotContain("independence");
  }

  @Test
  void rewriteRestoresThePreviousConfigOnRollback() throws IOException {
    shop("controller", "repository");
    final ImportLinterConfigGenerator generator = generator();
    generator.apply(project(), "layered", NO_EXTRAS);

    final ArchitectureTestChange rewrite = generator.apply(project(), "hexagonal", NO_EXTRAS);
    assertThat(rewrite.previous()).contains("layered layers");

    generator.rollback(rewrite);
    assertThat(Files.readString(root.resolve(".importlinter"))).contains("layered layers");
  }

  @Test
  void userOwnedContractsAreKeptAndPlannedAsSuch() throws IOException {
    shop("controller", "repository");
    Files.writeString(root.resolve(".importlinter"), "[importlinter]\nroot_package = shop\n");

    final PlannedFileChange plan = generator().plan(project(), "layered", NO_EXTRAS);

    assertThat(plan.action()).isEqualTo(PlannedFileChange.Action.PRESERVE_USER_OWNED);
  }

  private void shop(final String... layers) throws IOException {
    Files.createDirectories(root.resolve("src/shop"));
    Files.writeString(root.resolve("pyproject.toml"), "[project]\nname = \"shop\"\n");
    Files.writeString(root.resolve("src/shop/__init__.py"), "");
    for (final String layer : layers) {
      Files.createDirectories(root.resolve("src/shop").resolve(layer));
      Files.writeString(root.resolve("src/shop").resolve(layer).resolve("__init__.py"), "");
    }
  }

  private ImportLinterConfigGenerator generator() {
    return new ImportLinterConfigGenerator(new FileSystemPythonToolConfigs());
  }

  private Project project() {
    return new Project(root, Language.PYTHON, BuildTool.UV, Framework.NONE);
  }
}

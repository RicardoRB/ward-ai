package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.ArchitectureTestChange;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.domain.project.Toolchain;
import dev.ward.infrastructure.project.FileSystemNodeToolConfigs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Routes architecture rules to the generator of each toolchain. */
class ToolchainArchitectureRulesTest {
  private static final QualityPreset.ArchitectureExtras STANDARD =
      new QualityPreset.ArchitectureExtras(false, false, false);

  @TempDir Path root;

  private final ArchitectureTestGenerator jvm = new ArchitectureTestGenerator();
  private final DependencyCruiserConfigGenerator node =
      new DependencyCruiserConfigGenerator(new FileSystemNodeToolConfigs());
  private final HarmonizeRulesGenerator swift =
      new HarmonizeRulesGenerator((dir, tool) -> java.util.Optional.empty());
  private final ToolchainArchitectureRules rules =
      new ToolchainArchitectureRules(
          jvm,
          node,
          new ImportLinterConfigGenerator((dir, tool) -> java.util.Optional.empty()),
          swift);

  @Test
  void everyToolchainMapsToAGeneratorAndNodeUsesDependencyCruiser() {
    for (final Toolchain toolchain : Toolchain.values()) {
      assertThat(rules.generator(toolchain)).as(toolchain.name()).isNotNull();
    }
    assertThat(rules.generator(Toolchain.NODE)).isSameAs(node);
    assertThat(rules.generator(Toolchain.JVM)).isSameAs(jvm);
    assertThat(rules.generator(Toolchain.SWIFT)).isSameAs(swift);
  }

  @Test
  void nodeProjectGetsADependencyCruiserConfigNotAnArchUnitTest() throws IOException {
    rules.apply(project(Toolchain.NODE), "layered", STANDARD);

    assertThat(root.resolve(".dependency-cruiser.mjs")).exists();
    assertThat(root.resolve("src/test/java")).doesNotExist();
  }

  @Test
  void rewrittenNodeConfigIsRestoredOnRollback() throws IOException {
    rules.apply(project(Toolchain.NODE), "layered", STANDARD);
    final String layered = Files.readString(root.resolve(".dependency-cruiser.mjs"));
    final ArchitectureTestChange change =
        rules.apply(
            project(Toolchain.NODE),
            "clean",
            new QualityPreset.ArchitectureExtras(true, true, true));

    rules.rollback(change);

    assertThat(Files.readString(root.resolve(".dependency-cruiser.mjs"))).isEqualTo(layered);
  }

  private Project project(final Toolchain toolchain) {
    final BuildTool manager = toolchain == Toolchain.NODE ? BuildTool.NPM : BuildTool.MAVEN;
    final Language language = toolchain == Toolchain.NODE ? Language.TYPESCRIPT : Language.JAVA;
    return new Project(
        root,
        language,
        manager,
        Framework.NONE,
        false,
        false,
        root,
        Set.of(),
        "24",
        TaskRunner.NONE,
        null);
  }
}

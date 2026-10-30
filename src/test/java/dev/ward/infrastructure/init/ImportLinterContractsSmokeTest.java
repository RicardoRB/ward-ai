package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs a real {@code lint-imports} on the contracts Ward generates. Skipped unless {@code
 * ward.importlinter.binary} names the binary, as the other real-tool smoke tests are.
 */
@EnabledIfSystemProperty(named = "ward.importlinter.binary", matches = ".+")
class ImportLinterContractsSmokeTest {
  private static final QualityPreset.ArchitectureExtras NO_EXTRAS =
      new QualityPreset.ArchitectureExtras(false, false, false);

  @TempDir Path root;

  @Test
  void layeredContractPassesOnAllowedImportAndFailsOnForbiddenOne()
      throws IOException, InterruptedException {
    Files.writeString(root.resolve("pyproject.toml"), "[project]\nname = \"shop\"\n");
    Files.createDirectories(root.resolve("shop/controller"));
    Files.createDirectories(root.resolve("shop/repository"));
    for (final String package_ : List.of("shop", "shop/controller", "shop/repository")) {
      Files.createDirectories(root.resolve(package_));
      Files.writeString(root.resolve(package_).resolve("__init__.py"), "");
    }
    Files.writeString(root.resolve("shop/repository/store.py"), "STORE = 1\n");
    Files.writeString(root.resolve("shop/controller/api.py"), "CONTROLLER = 1\n");
    final Project project = new Project(root, Language.PYTHON, BuildTool.UV, Framework.NONE);
    Files.writeString(
        root.resolve(".importlinter"), ImportLinterContracts.render(project, "layered", NO_EXTRAS));

    assertThat(lintImports()).as("allowed layout").isZero();

    Files.writeString(
        root.resolve("shop/controller/api.py"), "from shop.repository import store\n");
    assertThat(lintImports()).as("controller imports repository").isNotZero();
  }

  private int lintImports() throws IOException, InterruptedException {
    final ProcessBuilder builder =
        new ProcessBuilder(System.getProperty("ward.importlinter.binary"))
            .directory(root.toFile())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD);
    return builder.start().waitFor();
  }
}

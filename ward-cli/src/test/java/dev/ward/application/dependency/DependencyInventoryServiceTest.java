package dev.ward.application.dependency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencySource;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DependencyInventoryServiceTest {
  private static final PrintStream ORIGINAL_OUT = System.out;
  private static final Project NPM =
      new Project(Path.of("/work/app"), Language.JAVASCRIPT, BuildTool.NPM, Framework.NONE);

  @Test
  void returnsWhatTheSourceOfTheBuildToolReads() {
    final DependencyInventory expected = new DependencyInventory(List.of());
    final DependencyInventoryService service =
        new DependencyInventoryService(Map.of(BuildTool.NPM, project -> expected));

    assertThat(service.inventory(NPM)).isSameAs(expected);
  }

  @Test
  void aFailingSourceNeverReturnsAPartialInventory() {
    final DependencySource failing =
        project -> {
          throw new DependencyInventoryException(
              "/work/app", "package-lock.json", "stale", "npm install");
        };
    final DependencyInventoryService service =
        new DependencyInventoryService(Map.of(BuildTool.NPM, failing));

    assertThatThrownBy(() -> service.inventory(NPM))
        .isInstanceOf(DependencyInventoryException.class)
        .hasMessageContaining("npm install");
  }

  @Test
  void anUnsupportedBuildToolFailsInsteadOfBeingEmpty() {
    final Project pdm =
        new Project(Path.of("/work/app"), Language.PYTHON, BuildTool.PDM, Framework.NONE);

    assertThatThrownBy(() -> new DependencyInventoryService(Map.of()).inventory(pdm))
        .isInstanceOf(DependencyInventoryException.class)
        .hasMessageContaining("PDM");
  }

  @Test
  void writesNothingToStandardOutput() {
    final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    try (PrintStream replacement = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
      System.setOut(replacement);
      new DependencyInventoryService(
              Map.of(BuildTool.NPM, project -> new DependencyInventory(List.of())))
          .inventory(NPM);
    } finally {
      System.setOut(ORIGINAL_OUT);
    }

    assertThat(captured.toString(StandardCharsets.UTF_8)).isEmpty();
  }
}

package dev.ward.domain.dependency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DependencySourcesTest {
  private static final Path ROOT = Path.of("/work/app");
  private static final DependencyInventory EMPTY = new DependencyInventory(List.of());

  private static Project project(final BuildTool tool) {
    return new Project(ROOT, Language.JAVA, tool, Framework.NONE);
  }

  @Test
  void everyBuildToolMapsToASource() {
    final Map<BuildTool, DependencySource> readers = new EnumMap<>(BuildTool.class);
    for (final BuildTool tool : BuildTool.values()) {
      readers.put(tool, ignored -> EMPTY);
      assertThat(DependencySources.forBuildTool(tool, readers)).isNotNull();
    }
  }

  @Test
  void unsupportedBuildToolsFailNamingTheTool() {
    for (final BuildTool tool :
        List.of(
            BuildTool.PDM,
            BuildTool.HATCH,
            BuildTool.GO,
            BuildTool.CARGO,
            BuildTool.DOTNET,
            BuildTool.TUIST,
            BuildTool.XCODEGEN)) {
      assertThatThrownBy(() -> DependencySources.forBuildTool(tool, Map.of()).read(project(tool)))
          .isInstanceOf(DependencyInventoryException.class)
          .hasMessageContaining(tool.displayName())
          .hasMessageContaining("not supported");
    }
  }

  @Test
  void aSupportedToolReturnsItsInjectedReader() {
    final DependencySource reader = ignored -> EMPTY;

    assertThat(DependencySources.forBuildTool(BuildTool.NPM, Map.of(BuildTool.NPM, reader)))
        .isSameAs(reader);
  }

  @Test
  void aSupportedToolWithoutAReaderFailsInsteadOfBeingEmpty() {
    assertThatThrownBy(
            () ->
                DependencySources.forBuildTool(BuildTool.UV, Map.of()).read(project(BuildTool.UV)))
        .isInstanceOf(DependencyInventoryException.class);
  }

  @Test
  void theMessageNamesProjectSubjectReasonAndFix() {
    final DependencyInventoryException failure =
        new DependencyInventoryException(
            "/p", "package-lock.json", "left-pad is missing", "npm install");

    assertThat(failure)
        .hasMessage(
            "Cannot read the dependencies of /p: package-lock.json: left-pad is missing."
                + " Fix: npm install");
  }
}

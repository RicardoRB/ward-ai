package dev.ward.domain.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ToolchainTest {
  @Test
  void everyBuildToolBelongsToItsToolchain() {
    final Map<BuildTool, Toolchain> toolchains =
        Arrays.stream(BuildTool.values())
            .collect(Collectors.toMap(tool -> tool, BuildTool::toolchain));

    assertThat(toolchains)
        .containsExactlyInAnyOrderEntriesOf(
            Map.ofEntries(
                Map.entry(BuildTool.MAVEN, Toolchain.JVM),
                Map.entry(BuildTool.GRADLE, Toolchain.JVM),
                Map.entry(BuildTool.NPM, Toolchain.NODE),
                Map.entry(BuildTool.PNPM, Toolchain.NODE),
                Map.entry(BuildTool.YARN, Toolchain.NODE),
                Map.entry(BuildTool.BUN, Toolchain.NODE),
                Map.entry(BuildTool.POETRY, Toolchain.PYTHON),
                Map.entry(BuildTool.PIP, Toolchain.PYTHON),
                Map.entry(BuildTool.GO, Toolchain.GO),
                Map.entry(BuildTool.CARGO, Toolchain.RUST),
                Map.entry(BuildTool.DOTNET, Toolchain.DOTNET)));
  }

  @Test
  void projectReportsTheToolchainOfItsBuildTool() {
    final Project project =
        new Project(Path.of("app"), Language.PYTHON, BuildTool.POETRY, Framework.NONE);

    assertThat(project.toolchain()).isEqualTo(Toolchain.PYTHON);
  }
}

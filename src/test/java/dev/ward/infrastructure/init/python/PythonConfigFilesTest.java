package dev.ward.infrastructure.init.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.PythonToolConfigs;
import dev.ward.infrastructure.init.ConfigTarget;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Ward writes Ruff, mypy and coverage configs only where the project has none of its own. */
class PythonConfigFilesTest {
  private static final Path ROOT = Path.of("/shop");

  @Test
  void writesEveryOwnedConfigWhenProjectHasNone() {
    final List<ConfigTarget> targets =
        PythonConfigFiles.targets(
            project(),
            (root, tool) -> Optional.empty(),
            QualityPreset.STANDARD,
            gates("ruff-format", "ruff", "mypy", "coverage"));

    assertThat(targets)
        .extracting(ConfigTarget::file)
        .containsExactly(
            ROOT.resolve("ruff.toml"), ROOT.resolve("mypy.ini"), ROOT.resolve(".coveragerc"));
    assertThat(targets.get(2).content())
        .startsWith("# managed-by: ward preset=standard\n")
        .contains("fail_under = 80");
  }

  @Test
  void keepsStandaloneUserConfigAndWritesNothingForSharedFile() {
    final PythonToolConfigs configs =
        (root, tool) ->
            switch (tool) {
              case "ruff" -> Optional.of(ROOT.resolve("ruff.toml"));
              case "coverage" -> Optional.of(ROOT.resolve("pyproject.toml"));
              default -> Optional.empty();
            };

    final List<ConfigTarget> targets =
        PythonConfigFiles.targets(
            project(), configs, QualityPreset.STANDARD, gates("ruff", "mypy", "coverage"));

    assertThat(targets)
        .extracting(ConfigTarget::file)
        .containsExactly(ROOT.resolve("ruff.toml"), ROOT.resolve("mypy.ini"));
    assertThat(targets.getFirst().disposition()).isEqualTo(ConfigTarget.Disposition.KEEP);
  }

  private static Project project() {
    return new Project(ROOT, Language.PYTHON, BuildTool.UV, Framework.NONE);
  }

  private static List<InitGateOption> gates(final String... ids) {
    return java.util.Arrays.stream(ids)
        .map(id -> new InitGateOption(id, List.of(), true, ""))
        .toList();
  }
}

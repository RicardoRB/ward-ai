package dev.ward.infrastructure.init.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.PresetRuleFileWriter;
import dev.ward.infrastructure.init.RuleFileTargetsFixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Re-running init with another preset rewrites Ward's own configs and leaves the user's alone. */
class PythonStrictRewriteTest {
  @TempDir Path root;

  @Test
  void strictAfterStandardRewritesWardsMypyConfig() throws IOException {
    final Project project = new Project(root, Language.PYTHON, BuildTool.UV, Framework.NONE);
    final List<InitGateOption> gates = List.of(new InitGateOption("mypy", List.of(), true, ""));
    final PresetRuleFileWriter writer =
        new PresetRuleFileWriter(RuleFileTargetsFixtures.defaults());
    writer.apply(project, QualityPreset.STANDARD, gates);
    assertThat(Files.readString(root.resolve("mypy.ini"))).doesNotContain("strict = True");

    final List<PlannedFileChange> plan = writer.plan(project, QualityPreset.STRICT, gates);
    assertThat(plan)
        .extracting(PlannedFileChange::action)
        .containsExactly(PlannedFileChange.Action.MODIFY);

    writer.apply(project, QualityPreset.STRICT, gates);
    assertThat(Files.readString(root.resolve("mypy.ini"))).contains("strict = True");
  }

  @Test
  void userOwnedMypyConfigIsNeverRewrittenByStrict() throws IOException {
    Files.writeString(
        root.resolve("pyproject.toml"),
        "[project]\nname = \"shop\"\n\n[tool.mypy]\nstrict = false\n");
    final Project project = new Project(root, Language.PYTHON, BuildTool.UV, Framework.NONE);
    final List<InitGateOption> gates = List.of(new InitGateOption("mypy", List.of(), true, ""));

    new PresetRuleFileWriter(
            RuleFileTargetsFixtures.of(
                manifestRoot -> dev.ward.domain.project.PackageManifest.EMPTY,
                (dir, tool) -> java.util.Optional.empty(),
                new dev.ward.infrastructure.project.FileSystemPythonToolConfigs()))
        .apply(project, QualityPreset.STRICT, gates);

    assertThat(Files.exists(root.resolve("mypy.ini"))).isFalse();
    assertThat(Files.readString(root.resolve("pyproject.toml"))).contains("strict = false");
  }
}

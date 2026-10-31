package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The ESLint line of the agent rules, for a generated, a layered and a kept config. */
class NodeEslintAgentRulesTest {
  private static final InitGateOption ESLINT =
      new InitGateOption("eslint", "ESLint", List.of("npx", "eslint"), true, "ok");

  @Test
  void generatedStrictConfigNamesTheStrictRules() {
    final String line = eslintLine(QualityPreset.STRICT, Set.of());

    assertThat(line)
        .contains("Ward's flat config (`eslint.config.*`) with ", "strict-type-checked");
  }

  @Test
  void layeredConfigNamesTheBaseFileAndWhoWins() {
    final String line = eslintLine(QualityPreset.STRICT, Set.of("eslint.base.mjs"));

    assertThat(line)
        .contains("imports `eslint.base.mjs`, the project's own config", "Ward's rules win")
        .doesNotContain("not applied");
  }

  @Test
  void keptConfigSaysItsRulesAreNotApplied() {
    final String line = eslintLine(QualityPreset.STANDARD, Set.of("eslint.config.mjs"));

    assertThat(line)
        .contains(
            "follow the rules in `eslint.config.mjs`",
            "the standard preset's ESLint rules are not applied to it");
  }

  @Test
  void legacyKeptConfigSaysItsRulesAreNotApplied() {
    final String line = eslintLine(QualityPreset.STRICT, Set.of(".eslintrc.json"));

    assertThat(line)
        .contains(
            "follow the rules in `.eslintrc.json`",
            "the strict preset's ESLint rules are not applied to it");
  }

  private static String eslintLine(final QualityPreset preset, final Set<String> owned) {
    final Project project =
        new Project(
            Path.of("/repo"),
            Language.TYPESCRIPT,
            BuildTool.NPM,
            Framework.NONE,
            false,
            false,
            Path.of("/repo"),
            Set.of(),
            "24",
            TaskRunner.NONE,
            null);
    return NodeAgentRules.lines(project, List.of(ESLINT), preset, owned).getFirst();
  }
}

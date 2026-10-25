package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The Biome line of the agent rules, for Ward's config and for a user's own config. */
class NodeBiomeAgentRulesTest {
  private static final Project TYPESCRIPT =
      new Project(
          Path.of("shop"),
          Language.TYPESCRIPT,
          BuildTool.NPM,
          Framework.NONE,
          false,
          false,
          Path.of("shop"),
          Set.of(),
          "24");
  private static final InitGateOption BIOME =
      new InitGateOption("biome", "Biome", List.of("npx", "biome", "ci", "."), true, "ok");

  @Test
  void standardRulesNameWardsConfigAndNeitherPrettierNorEslint() {
    final String line = biomeLine(QualityPreset.STANDARD, Set.of());

    assertThat(line)
        .contains("Biome", "Ward's `biome.jsonc`", "npx biome ci .")
        .doesNotContain("Prettier", "ESLint", "warnings fail");
  }

  @Test
  void strictRulesWithAUserConfigFollowThatConfig() {
    final String line = biomeLine(QualityPreset.STRICT, Set.of("biome.json"));

    assertThat(line)
        .contains("follow the rules in `biome.json`", "warnings fail the gate")
        .contains("replace typescript-eslint's `strict-type-checked`");
  }

  private static String biomeLine(final QualityPreset preset, final Set<String> owned) {
    return NodeAgentRules.lines(TYPESCRIPT, List.of(BIOME), preset, owned).getFirst();
  }
}

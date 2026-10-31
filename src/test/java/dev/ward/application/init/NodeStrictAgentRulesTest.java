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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The agent rules of a Node project under the strict preset. */
class NodeStrictAgentRulesTest {
  private static final Path ROOT = Path.of("/repo");
  private static final String STRYKER_MINIMUM = "mutation score of at least 60%";
  private static final String TYPESCRIPT_ESLINT = "typescript-eslint";

  @Test
  void strictTypeScriptRulesNameTheStrictRulesAndMinimums() {
    final String body =
        rules(Language.TYPESCRIPT, BuildTool.NPM, QualityPreset.STRICT, "eslint", "vitest");

    assertThat(body)
        .contains(
            "typescript-eslint's `strict-type-checked` rules",
            "Tests (Vitest)",
            "at least 85% line and 75% branch coverage",
            "Mutation testing (Stryker): `npx stryker` in `ci`",
            STRYKER_MINIMUM)
        .doesNotContain("JaCoCo", "PIT");
  }

  @Test
  void strictJavaScriptRulesNameNoTypeScriptTool() {
    final String body =
        rules(Language.JAVASCRIPT, BuildTool.NPM, QualityPreset.STRICT, "eslint", "jest");

    assertThat(body)
        .contains(
            "ESLint's recommended JavaScript rules",
            "Tests (Jest)",
            "at least 85% line and 75% branch coverage",
            STRYKER_MINIMUM)
        .doesNotContain("tsc", TYPESCRIPT_ESLINT);
  }

  @Test
  void strictBunRulesNameOnlyTheLineMinimum() {
    final String body = rules(Language.TYPESCRIPT, BuildTool.BUN, QualityPreset.STRICT, "bun-test");

    assertThat(body)
        .contains("at least 85% line coverage set in `bunfig.toml`")
        .doesNotContain("branch");
  }

  @Test
  void strykerWithoutStrictNamesNoMinimum() {
    final String body = rules(Language.TYPESCRIPT, BuildTool.NPM, QualityPreset.STANDARD, "vitest");

    assertThat(body)
        .contains("Mutation testing (Stryker)", "at least 70% line coverage")
        .doesNotContain(STRYKER_MINIMUM, "branch");
  }

  private static String rules(
      final Language language,
      final BuildTool manager,
      final QualityPreset preset,
      final String... ids) {
    final List<InitGateOption> gates =
        Stream.concat(List.of(ids).stream(), Stream.of("stryker"))
            .map(id -> new InitGateOption(id, id, List.of("npx", id), true, "ok"))
            .toList();
    final Project project =
        new Project(
            ROOT,
            language,
            manager,
            Framework.NONE,
            false,
            false,
            ROOT,
            Set.of(),
            "24",
            TaskRunner.NONE,
            null);
    return AgentRulesTemplate.render(project, gates, preset, List.of()).body();
  }
}

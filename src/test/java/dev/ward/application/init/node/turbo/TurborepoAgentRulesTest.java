package dev.ward.application.init.node.turbo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.node.NodeAgentRules;
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

/** Agent rules for a Turborepo root tell agents to run the gates through Ward. */
class TurborepoAgentRulesTest {
  @Test
  void turboRootRulesSayToRunGatesThroughWardNotPerPackage() {
    final Project root =
        new Project(
            Path.of("/work/monorepo"),
            Language.TYPESCRIPT,
            BuildTool.PNPM,
            Framework.NONE,
            false,
            false,
            Path.of("/work/monorepo"),
            Set.of(),
            "24",
            TaskRunner.TURBO,
            null);
    final List<InitGateOption> gates =
        List.of(
            new InitGateOption(
                "tsc",
                "Type-checks.",
                List.of("pnpm", "exec", "turbo", "run", "check-types"),
                true,
                ""));

    final List<String> lines = NodeAgentRules.lines(root, gates, QualityPreset.STANDARD, Set.of());

    assertThat(lines.getFirst()).contains("Turborepo workspace").contains("through `ward check`");
    assertThat(lines).anyMatch(line -> line.contains("pnpm exec turbo run check-types"));
  }
}

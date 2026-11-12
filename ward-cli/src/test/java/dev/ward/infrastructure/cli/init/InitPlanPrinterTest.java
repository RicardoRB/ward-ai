package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.agent.PlannedFileChange.Action;
import dev.ward.domain.init.InitPlan;
import dev.ward.domain.init.PlannedGateInstallation;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class InitPlanPrinterTest {
  private static final Path ROOT = Path.of("/project");

  private final StringWriter output = new StringWriter();

  @Test
  void rendersActionsDetailsAndGateInstallationsRelativeToTheProject() {
    final InitPlan plan =
        new InitPlan(
            List.of(
                change("ward.toml", Action.CREATE),
                new PlannedFileChange(
                    ROOT.resolve("pom.xml"), Action.MODIFY, List.of("+ maven-pmd-plugin 3.28.0")),
                change("config/checkstyle.xml", Action.PRESERVE_USER_OWNED),
                change(".claude/settings.json", Action.CONFLICT),
                change(".claude/hooks/old", Action.REMOVE),
                change("config/pmd-ruleset.xml", Action.UNCHANGED)),
            List.of(new PlannedGateInstallation("zap-baseline.py", "https://zap.example/docs")));

    new InitResultPrinter(new PrintWriter(output, true)).printPlan(plan, ROOT);

    assertThat(output.toString())
        .contains(
            "Ward will make these changes:",
            "  create     ward.toml",
            "  modify     pom.xml",
            "               + maven-pmd-plugin 3.28.0",
            "  keep       config/checkstyle.xml  (user-owned, not changed)",
            "  conflict   .claude/settings.json  (user-owned, blocks initialization)",
            "  remove     .claude/hooks/old",
            "  guidance   zap-baseline.py: https://zap.example/docs")
        .doesNotContain("pmd-ruleset.xml");
  }

  @Test
  void emptyPlanSaysNothingWouldChange() {
    new InitResultPrinter(new PrintWriter(output, true))
        .printPlan(new InitPlan(List.of(change("pom.xml", Action.UNCHANGED))), ROOT);

    assertThat(output.toString()).contains("No files would change.");
  }

  private static PlannedFileChange change(final String path, final Action action) {
    return new PlannedFileChange(ROOT.resolve(path), action);
  }
}

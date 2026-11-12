package dev.ward.infrastructure.cli.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateSection;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.terminal.TerminalCapabilities;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GateLinesTest {
  private static final String GATE = "ward";
  private static final int ERRORS = 3;
  private static final int WARNINGS = 7;

  private final Project project =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.SPRING_BOOT);

  @Test
  void textReportListsEachSectionOfGateAndJsonKeepsOneCheck() {
    final CheckReport report =
        report(
            GateResult.builder(GATE, GateStatus.FAILED)
                .errors(ERRORS)
                .build()
                .withSections(
                    List.of(
                        new GateSection("naming", 0, 0),
                        new GateSection("style", ERRORS, 0),
                        new GateSection("docs", 0, WARNINGS))));

    assertThat(new TextReportRenderer().render(report))
        .contains("✓ ward · naming", "✗ ward · style — 3 errors", "✓ ward · docs — 7 warnings")
        .contains("0 passed · 1 failed · 0 skipped");
    final JsonTree checks =
        JsonTree.parse(new JsonReportRenderer(new ForyJsonCodec()).render(report)).get("checks");
    assertThat(checks.get(0).get("name").asString()).isEqualTo(GATE);
    assertThat(checks.get(0).has("sections")).isFalse();
  }

  @Test
  void gateThatCouldNotRunKeepsItsSingleLine() {
    final CheckReport report =
        report(
            GateResult.builder(GATE, GateStatus.EXECUTION_ERROR)
                .build()
                .withSections(List.of(new GateSection("style", 0, 0))));

    assertThat(new TextReportRenderer().render(report))
        .contains("✗ ward — error")
        .doesNotContain("ward · style");
  }

  @Test
  void jsonAndSarifAreTheSameWithAndWithoutSections() {
    final GateResult plain = GateResult.builder(GATE, GateStatus.FAILED).errors(ERRORS).build();
    final GateResult sectioned =
        plain.withSections(
            List.of(new GateSection("naming", 0, 0), new GateSection("style", ERRORS, 0)));

    assertThat(new JsonReportRenderer(new ForyJsonCodec()).render(report(sectioned)))
        .isEqualTo(new JsonReportRenderer(new ForyJsonCodec()).render(report(plain)));
    assertThat(new SarifReportRenderer(new ForyJsonCodec()).render(report(sectioned)))
        .isEqualTo(new SarifReportRenderer(new ForyJsonCodec()).render(report(plain)));
  }

  @Test
  void singleFindingIsNotPlural() {
    assertThat(GateLines.of(failedWith(new GateSection("style", 1, 0))))
        .isEqualTo("✗ ward · style — 1 error");
    assertThat(GateLines.of(failedWith(new GateSection("docs", 0, 1))))
        .isEqualTo("✓ ward · docs — 1 warning");
  }

  private static GateResult failedWith(final GateSection section) {
    return GateResult.builder(GATE, GateStatus.FAILED).build().withSections(List.of(section));
  }

  @Test
  void singleSectionPrintsOneLine() {
    final GateResult result =
        GateResult.builder(GATE, GateStatus.PASSED)
            .build()
            .withSections(List.of(new GateSection("other", 0, 0)));

    assertThat(GateLines.of(result)).isEqualTo("✓ ward · other");
  }

  @Test
  void interactiveTerminalReplacesRunningLineWithEachStepAndThenSections() {
    final StringWriter out = new StringWriter();
    final TextReportRenderer renderer = new TextReportRenderer(terminal(true), "dev");
    renderer.begin(new PrintWriter(out));

    renderer.gateStarted(GATE);
    renderer.gateStep(GATE, "loading rules");
    renderer.gateFinished(
        GateResult.builder(GATE, GateStatus.PASSED)
            .build()
            .withSections(List.of(new GateSection("style", 0, 0))));

    assertThat(out.toString())
        .contains("… ward\r\033[2K… ward: loading rules\r\033[2K✓ ward · style");
  }

  @Test
  void plainTerminalPrintsNoPhaseLineAndNoEscapes() {
    final StringWriter out = new StringWriter();
    final TextReportRenderer renderer = new TextReportRenderer(terminal(false), "dev");
    renderer.begin(new PrintWriter(out));

    renderer.gateStarted(GATE);
    renderer.gateStep(GATE, "loading rules");
    renderer.gateFinished(
        GateResult.builder(GATE, GateStatus.PASSED)
            .build()
            .withSections(List.of(new GateSection("style", 0, 0))));

    assertThat(out.toString()).contains("✓ ward · style").doesNotContain("…", "\033");
  }

  private static TerminalCapabilities terminal(final boolean interactive) {
    return new TerminalCapabilities() {
      @Override
      public boolean interactive() {
        return interactive;
      }

      @Override
      public boolean colorEnabled() {
        return false;
      }

      @Override
      public boolean consoleAttached() {
        return interactive;
      }
    };
  }

  private CheckReport report(final GateResult result) {
    return new CheckReport(project, List.of(result));
  }
}

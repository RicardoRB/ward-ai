package dev.ward.infrastructure.cli.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.GatePrerequisite;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SarifPrerequisiteReportTest {
  private static final String RUNS = "runs";
  private static final String RESULTS = "results";

  private final Project project =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  private JsonTree render(final GateResult... results) {
    return JsonTree.parse(
        new SarifReportRenderer(new ForyJsonCodec())
            .render(new CheckReport(project, List.of(results))));
  }

  private static GateResult withPrerequisite(
      final String name, final GatePrerequisite prerequisite, final String summary) {
    return GateResult.builder(name, GateStatus.UNAVAILABLE)
        .command(List.of("x"))
        .exitCode(-1)
        .duration(Duration.ZERO)
        .stdout("")
        .stderr("")
        .summary(summary)
        .build()
        .withPrerequisite(prerequisite);
  }

  @Test
  void missingToolIsFailedInvocationWithoutResults() {
    final JsonTree run =
        render(
                withPrerequisite(
                    "trivy",
                    new GatePrerequisite(
                        GatePrerequisite.Kind.EXECUTABLE,
                        "trivy",
                        null,
                        null,
                        "Install trivy: https://trivy.dev"),
                    "Gate 'trivy' could not start 'trivy'. Install trivy: https://trivy.dev"))
            .get(RUNS)
            .get(0);

    assertThat(run.get(RESULTS).size()).isZero();
    final JsonTree invocation = run.get("invocations").get(0);
    assertThat(invocation.get("executionSuccessful").asBoolean()).isFalse();
    final JsonTree notification = invocation.get("toolExecutionNotifications").get(0);
    assertThat(notification.get("level").asString()).isEqualTo("error");
    assertThat(notification.get("message").get("text").asString()).contains("trivy");
  }

  @Test
  void oldJdkIsFailedInvocationNamingBothVersions() {
    final JsonTree run =
        render(
                withPrerequisite(
                    "compile",
                    new GatePrerequisite(
                        GatePrerequisite.Kind.RUNTIME, null, "25", "17", "Run on JDK 25."),
                    "Gate 'compile' needs Java 25 but the build runs on Java 17. Run on JDK 25."))
            .get(RUNS)
            .get(0);

    assertThat(run.get(RESULTS).size()).isZero();
    assertThat(run.get("invocations").get(0).get("executionSuccessful").asBoolean()).isFalse();
    final String text =
        run.get("invocations")
            .get(0)
            .get("toolExecutionNotifications")
            .get(0)
            .get("message")
            .get("text")
            .asString();
    assertThat(text).contains("Java 25").contains("Java 17");
  }
}

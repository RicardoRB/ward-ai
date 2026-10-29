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

class JsonReportRendererTest {
  private static final int SCHEMA_VERSION = 2;

  private final Project project =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  private static GateResult unavailableTrivy() {
    return GateResult.builder("trivy", GateStatus.UNAVAILABLE)
        .command(List.of("trivy", "image"))
        .exitCode(-1)
        .duration(Duration.ZERO)
        .stdout("")
        .stderr("")
        .build()
        .withPrerequisite(
            new GatePrerequisite(
                GatePrerequisite.Kind.EXECUTABLE,
                "trivy",
                null,
                null,
                "Install trivy or add it to PATH."));
  }

  private static GateResult failedCheckstyle() {
    return GateResult.builder("checkstyle", GateStatus.FAILED)
        .command(List.of("./mvnw", "checkstyle:check"))
        .exitCode(1)
        .duration(Duration.ZERO)
        .stdout("")
        .stderr("")
        .build();
  }

  private JsonTree render(final GateResult... results) {
    return JsonTree.parse(
        new JsonReportRenderer(new ForyJsonCodec())
            .render(new CheckReport(project, List.of(results))));
  }

  @Test
  void missingExecutableIsReportedAsExecutablePrerequisite() {
    final JsonTree prerequisite =
        render(unavailableTrivy()).get("checks").get(0).get("prerequisite");

    assertThat(prerequisite.get("kind").asString()).isEqualTo("executable");
    assertThat(prerequisite.get("executable").asString()).isEqualTo("trivy");
    assertThat(prerequisite.get("remediation").asString())
        .isEqualTo("Install trivy or add it to PATH.");
    assertThat(prerequisite.has("required")).isFalse();
    assertThat(prerequisite.has("detected")).isFalse();
  }

  @Test
  void failedGateHasNoPrerequisiteObject() {
    final JsonTree check = render(failedCheckstyle()).get("checks").get(0);

    assertThat(check.get("status").asString()).isEqualTo("FAILED");
    assertThat(check.has("prerequisite")).isFalse();
  }

  @Test
  void schemaVersionDoesNotChange() {
    assertThat(render(unavailableTrivy()).get("schemaVersion").asInt()).isEqualTo(SCHEMA_VERSION);
  }
}

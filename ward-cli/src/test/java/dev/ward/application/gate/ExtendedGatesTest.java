package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExtendedGatesTest {
  private static final String TRIVY = "trivy";
  private static final List<String> GATE_IDENTIFIERS =
      List.of("semgrep", "enforcer", "api-compat", "compliance", "architecture");
  private static final Project PROJECT =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  private static WardConfiguration config(final String id, final List<String> command) {
    final Map<String, GateConfiguration> gates = new LinkedHashMap<>();
    gates.put(id, new GateConfiguration(id, true, command));
    return new WardConfiguration(1, gates);
  }

  @Test
  void acceptsEveryNewAndLegacyIdentifierInConfigurationOrder() {
    final Map<String, GateConfiguration> gates = new LinkedHashMap<>();
    GATE_IDENTIFIERS.forEach(id -> gates.put(id, new GateConfiguration(id, true, List.of("tool"))));
    final List<QualityGate> created =
        new QualityGateFactory(new FakeCommandExecutor(0, "", ""))
            .create(new WardConfiguration(1, gates));

    assertThat(created).extracting(QualityGate::name).containsExactlyElementsOf(gates.keySet());
  }

  @Test
  void unknownGateErrorListsNewIdentifiers() {
    final QualityGateFactory factory = new QualityGateFactory(new FakeCommandExecutor(0, "", ""));

    assertThatThrownBy(() -> factory.create(config("nope", List.of("x"))))
        .isInstanceOf(WardException.class)
        .hasMessageContaining(
            "zap", "api-compat", "pmd", "openapi-lint", "openapi-diff", "openapi-compat");
  }

  @Test
  void customGateRunsThroughCommandExecutor() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "ok", "");
    final GateResult result =
        new QualityGateFactory(executor)
            .create(config("command", List.of("mvnw", "lint:check")))
            .getFirst()
            .execute(PROJECT);
    assertThat(result.name()).isEqualTo("command");
    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void removedNodeLintGateTypesAreRejectedAsUnknown() {
    final QualityGateFactory factory = new QualityGateFactory(new FakeCommandExecutor(0, "", ""));

    assertThatThrownBy(() -> factory.create(config("eslint", List.of("npx", "eslint"))))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("'eslint'");
    assertThatThrownBy(() -> factory.create(config("biome", List.of("npx", "biome"))))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("'biome'");
  }

  @Test
  void zapRequiresExplicitTarget() {
    final QualityGateFactory factory = new QualityGateFactory(new FakeCommandExecutor(0, "", ""));

    assertThatThrownBy(() -> factory.create(config("zap", List.of("zap-baseline.py"))))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("target");
    assertThatThrownBy(() -> factory.create(config("zap", List.of("zap-baseline.py", "-t"))))
        .isInstanceOf(WardException.class);
    assertThatThrownBy(
            () ->
                factory.create(
                    config(
                        "zap", List.of("zap-baseline.py", "-t", ZapTargetValidation.PLACEHOLDER))))
        .isInstanceOf(WardException.class);
    assertThatCode(
            () ->
                factory.create(
                    config("zap", List.of("zap-baseline.py", "-t", "http://localhost:8080"))))
        .doesNotThrowAnyException();
  }

  @Test
  void disabledZapWithoutTargetIsSkippedNotRejected() {
    final Map<String, GateConfiguration> gates =
        Map.of("zap", new GateConfiguration("zap", false, List.of("z")));

    assertThat(
            new QualityGateFactory(new FakeCommandExecutor(0, "", ""))
                .create(new WardConfiguration(1, gates)))
        .hasSize(1);
  }

  @Test
  void missingBinaryReportsInstallHint() {
    final CommandExecutor missing =
        (command, dir) ->
            new CommandResult(-1, "", "", Duration.ZERO, "Could not start [trivy]: not found");
    final GateResult result =
        new QualityGateRunner()
            .run(PROJECT, List.of(new CommandQualityGate(TRIVY, missing, List.of(TRIVY, "detect"))))
            .results()
            .getFirst();

    assertThat(result.status()).isEqualTo(GateStatus.UNAVAILABLE);
    assertThat(result.summary()).contains(TRIVY).contains("Install trivy");
  }

  @Test
  void failedSecretScanDoesNotAddMatchedValuesToSummary() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(1, "", "leaks found: 1");
    final GateResult result =
        new CommandQualityGate(TRIVY, executor, List.of(TRIVY, "detect", "--redact"))
            .execute(PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(String.valueOf(result.summary())).doesNotContain("AKIA");
  }
}

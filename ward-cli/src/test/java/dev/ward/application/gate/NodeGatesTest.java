package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NodeGatesTest {
  private static final Path ROOT = Path.of("/repo/web");
  private static final Project PROJECT =
      new Project(ROOT, Language.TYPESCRIPT, BuildTool.PNPM, Framework.NONE);
  private static final List<String> ESLINT =
      List.of("pnpm", "exec", "eslint", ".", "--format", "json");
  private static final List<String> TSC =
      List.of("pnpm", "exec", "tsc", "--noEmit", "--pretty", "false");
  private static final List<String> VITEST = List.of("pnpm", "exec", "vitest", "run");

  private static WardConfiguration config(final Map<String, List<String>> commands) {
    final Map<String, GateConfiguration> gates = new LinkedHashMap<>();
    commands.forEach((id, command) -> gates.put(id, new GateConfiguration(id, true, command)));
    return new WardConfiguration(1, gates);
  }

  @Test
  void nodeGatesRunTheirConfiguredCommandFromTheProjectRoot() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");
    final Map<String, List<String>> commands = new LinkedHashMap<>();
    commands.put("eslint", ESLINT);
    commands.put("tsc", TSC);
    commands.put("vitest", VITEST);

    final List<QualityGate> gates = new QualityGateFactory(executor).create(config(commands));
    final List<GateResult> results = gates.stream().map(gate -> gate.execute(PROJECT)).toList();

    assertThat(results).extracting(GateResult::name).containsExactly("eslint", "tsc", "vitest");
    assertThat(results).extracting(GateResult::status).containsOnly(GateStatus.PASSED);
    assertThat(executor.commands).containsExactly(ESLINT, TSC, VITEST);
    assertThat(executor.workingDirectories).containsOnly(ROOT);
  }

  @Test
  void everyNodeGateIdentifierIsAccepted() {
    final Map<String, List<String>> commands = new LinkedHashMap<>();
    List.of(
            "tsc",
            "eslint",
            "prettier",
            "vitest",
            "jest",
            "bun-test",
            "stryker",
            "dependency-cruiser")
        .forEach(id -> commands.put(id, List.of(id)));

    final List<QualityGate> gates =
        new QualityGateFactory(new FakeCommandExecutor(0, "", "")).create(config(commands));

    assertThat(gates).extracting(QualityGate::name).containsExactlyElementsOf(commands.keySet());
  }

  @Test
  void failingToolFailsTheGate() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(2, "", "[warn] src/app.ts");

    final GateResult result =
        new QualityGateFactory(executor)
            .create(config(Map.of("prettier", List.of("npx", "prettier", "--check", "."))))
            .getFirst()
            .execute(PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
  }

  @Test
  void unknownGateErrorListsNodeIdentifiers() {
    final QualityGateFactory factory = new QualityGateFactory(new FakeCommandExecutor(0, "", ""));

    assertThatThrownBy(() -> factory.create(config(Map.of("nope", List.of("x")))))
        .isInstanceOf(WardException.class)
        .hasMessageContaining(
            "tsc",
            "eslint",
            "prettier",
            "vitest",
            "jest",
            "bun-test",
            "stryker",
            "dependency-cruiser");
  }
}

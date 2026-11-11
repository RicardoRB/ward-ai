package dev.ward.application.init.node;

import dev.ward.application.init.CrapInit;
import dev.ward.application.init.CrapSetup;
import dev.ward.application.init.InitInputs;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The {@code crap} setup of Node projects: reporter flags on the test gate that writes lcov. */
final class NodeCrap {
  /** The test gates that can write lcov; Angular's {@code ng test} cannot. */
  private static final Set<String> LCOV_GATES = Set.of("vitest", "jest", "bun-test");

  private NodeCrap() {}

  /**
   * Returns the setup, or empty for Angular and Turborepo roots, whose test gate cannot be used.
   */
  static Optional<CrapSetup> of(final Project project, final InitInputs inputs) {
    final String test = NodeTools.testGate(project, inputs.manifests().read(project.root()));
    if (project.taskRunner().isRootRunner() || !LCOV_GATES.contains(test)) {
      return Optional.empty();
    }
    final NodeTools tools =
        NodeTools.of(project, inputs.manifests().read(project.root()), inputs, null);
    return Optional.of(gates -> place(gates, tools));
  }

  private static List<InitGateOption> place(
      final List<InitGateOption> gates, final NodeTools tools) {
    if (gates.stream().noneMatch(gate -> SupportedQualityGates.CRAP.equals(gate.id()))) {
      return gates;
    }
    final String test = tools.testGate();
    return CrapInit.place(
        gates.stream().map(gate -> test.equals(gate.id()) ? withFlags(gate, tools) : gate).toList(),
        test,
        List.of(),
        List.of(),
        Map.of());
  }

  private static InitGateOption withFlags(final InitGateOption gate, final NodeTools tools) {
    final List<String> command = new ArrayList<>(gate.command());
    command.addAll(tools.crapFlags());
    return new InitGateOption(
        gate.id(),
        gate.description(),
        command,
        gate.available(),
        gate.availabilityMessage(),
        gate.settings());
  }
}

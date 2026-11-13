package dev.ward.application.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.FormatGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.PresetGateOrder;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Resolves a selection's gate ids into configured gates; shared by planning and applying. */
final class SelectedGates {
  private static final Set<String> NODE_TEST_GATES = Set.of("vitest", "jest", "bun-test");

  private SelectedGates() {}

  /**
   * Returns the selected gates with the selected preset gates first, in preset order, followed by
   * the other selected gates. Unselected preset gates are not added.
   */
  static List<InitGateOption> resolve(
      final InitSetupCatalog catalog, final Project project, final InitSelection selection) {
    final List<String> ids =
        Stream.concat(
                (selection.preset() == null
                        ? selection.gates()
                        : PresetGateOrder.orderSelected(
                            catalog.presetGates(project, selection.preset()), selection.gates()))
                    .stream(),
                Stream.of(FormatGates.WARD_FORMAT))
            .distinct()
            .toList();
    final List<InitGateOption> gates =
        ids.stream().map(id -> option(catalog, project, selection, id)).toList();
    if (gates.isEmpty()) {
      throw new WardException("Select at least one quality gate.");
    }
    requireNodeTestGate(project, gates);
    return InitToolchains.find(project)
        .flatMap(support -> support.crap(project, catalog.inputs))
        .map(setup -> setup.apply(gates))
        .orElse(gates);
  }

  /** A Node {@code coverage} check reads what a test runner wrote, so it needs that gate. */
  private static void requireNodeTestGate(final Project project, final List<InitGateOption> gates) {
    final List<String> ids = gates.stream().map(InitGateOption::id).toList();
    if (project.toolchain() == Toolchain.NODE
        && ids.contains(SupportedQualityGates.COVERAGE)
        && ids.stream().noneMatch(NODE_TEST_GATES::contains)) {
      throw new WardException(
          "The coverage gate needs the vitest, jest or bun-test gate, which writes the report it"
              + " reads. Select both gates.");
    }
  }

  /** The {@code ward-format} gate: it has no command, because Ward runs it in-process. */
  private static InitGateOption wardFormat() {
    return new InitGateOption(
        FormatGates.WARD_FORMAT,
        "Checks formatting with Ward's native formatter, configured by .editorconfig.",
        List.of(),
        true,
        null,
        Map.of());
  }

  /** Returns the configured gate for {@code id}, with the {@code ward-format} gate built here. */
  private static InitGateOption option(
      final InitSetupCatalog catalog,
      final Project project,
      final InitSelection selection,
      final String id) {
    if (FormatGates.WARD_FORMAT.equals(id)) {
      return wardFormat();
    }
    return configured(catalog, project, id, selection.preset());
  }

  /** Returns the offered gate {@code id}, with the command {@code preset} configures. */
  private static InitGateOption configured(
      final InitSetupCatalog catalog,
      final Project project,
      final String id,
      final QualityPreset preset) {
    final InitGateOption offered = catalog.gate(project, id);
    return preset == null
        ? offered
        : catalog.gates(project, preset).stream()
            .filter(gate -> gate.id().equals(id))
            .findFirst()
            .orElse(offered);
  }
}

package dev.ward.application.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.FormatGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.PresetGateOrder;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Resolves a selection's gate ids into configured gates; shared by planning and applying. */
final class SelectedGates {
  private SelectedGates() {}

  /**
   * Returns the selected gates with the selected preset gates first, in preset order, followed by
   * the other selected gates and the OpenSpec validations. Unselected preset gates are not added.
   */
  static List<InitGateOption> resolve(
      final InitSetupCatalog catalog,
      final Project project,
      final InitSelection selection,
      final String openSpecExecutable) {
    final List<String> ids =
        Stream.concat(
                Stream.concat(
                    (selection.preset() == null
                            ? selection.gates()
                            : PresetGateOrder.orderSelected(
                                catalog.presetGates(project, selection.preset()),
                                selection.gates()))
                        .stream(),
                    selection.openSpecValidations().stream()),
                Stream.of(FormatGates.WARD_FORMAT))
            .distinct()
            .toList();
    final List<InitGateOption> gates =
        ids.stream()
            .map(id -> option(catalog, project, selection, openSpecExecutable, id))
            .toList();
    if (gates.isEmpty() && selection.tools().isEmpty()) {
      throw new WardException("Select at least one quality gate.");
    }
    return gates;
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
      final String openSpecExecutable,
      final String id) {
    if (FormatGates.WARD_FORMAT.equals(id)) {
      return wardFormat();
    }
    return catalog.openSpecValidations().contains(id)
        ? catalog.openSpecValidation(project, id, openSpecExecutable)
        : configured(catalog, project, id, selection.preset());
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

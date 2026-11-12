package dev.ward.application.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.FormatGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.PresetGateOrder;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Resolves a selection's gate ids into configured gates; shared by planning and applying. */
final class SelectedGates {
  private static final String TRIVY = "trivy";
  private static final String OFFLINE_SCAN = "--offline-scan";

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
    return offlineTrivy(gates, project);
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

  /**
   * Maven Central rate-limits CI runners, so Trivy resolves POMs from the local repository when an
   * earlier Maven or Gradle gate has already filled it.
   */
  static List<InitGateOption> offlineTrivy(
      final List<InitGateOption> gates, final Project project) {
    if (project.toolchain() != Toolchain.JVM) {
      return gates;
    }
    final int firstBuild =
        IntStream.range(0, gates.size())
            .filter(i -> isBuildGate(gates.get(i), project))
            .findFirst()
            .orElse(gates.size());
    return IntStream.range(0, gates.size())
        .mapToObj(i -> i > firstBuild ? offline(gates.get(i)) : gates.get(i))
        .toList();
  }

  private static boolean isBuildGate(final InitGateOption gate, final Project project) {
    return !gate.command().isEmpty() && project.buildExecutable().equals(gate.command().getFirst());
  }

  private static InitGateOption offline(final InitGateOption gate) {
    if (!TRIVY.equals(gate.id()) || !isFilesystemScan(gate.command())) {
      return gate;
    }
    final List<String> command = new ArrayList<>(gate.command());
    command.add(2, OFFLINE_SCAN);
    return new InitGateOption(
        gate.id(), gate.description(), command, gate.available(), gate.availabilityMessage());
  }

  private static boolean isFilesystemScan(final List<String> command) {
    return command.size() > 1 && "fs".equals(command.get(1)) && !command.contains(OFFLINE_SCAN);
  }
}

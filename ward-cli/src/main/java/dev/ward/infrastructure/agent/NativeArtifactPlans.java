package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.PlannedFileChange;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Builds a native adapter's artifact plan and the file changes installing it would make. */
final class NativeArtifactPlans {
  private NativeArtifactPlans() {}

  /** Builds the files an adapter would write. */
  static NativeArtifactPlan of(final NativeHarnessAdapter adapter, final Path projectRoot) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final NativeAgentIntegration.Definition definition = adapter.definition();
    return new NativeArtifactPlan(
        adapter.id(),
        root.resolve(definition.configFile()),
        definition.hasHookFile() ? root.resolve(definition.hookFile()) : null,
        NativeHookText.marker(adapter.id()),
        adapter.configContent(),
        definition.hasHookFile() ? adapter.hookContent() : null,
        adapter.capabilities(),
        definition.legacyFiles().stream().map(root::resolve).toList());
  }

  /** Plans an install with the same ownership decision {@link #integrate} writes with. */
  static List<PlannedFileChange> plannedChanges(final NativeArtifactPlan plan) {
    try {
      final List<PlannedFileChange> changes = new ArrayList<>();
      changes.add(
          new PlannedFileChange(
              plan.configPath(),
              IntegrationArtifacts.decide(plan.configPath(), plan.marker(), plan.configContent())));
      if (plan.hasHook()) {
        changes.add(
            new PlannedFileChange(
                plan.hookPath(),
                IntegrationArtifacts.decide(plan.hookPath(), plan.marker(), plan.hookContent())));
      }
      for (final Path legacy : plan.legacyPaths()) {
        changes.addAll(plan.legacy(legacy).plan());
      }
      return changes;
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Could not plan " + plan.id() + " integration: " + exception.getMessage(), exception);
    }
  }
}

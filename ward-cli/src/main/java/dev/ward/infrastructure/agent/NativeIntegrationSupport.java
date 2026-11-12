package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.IntegrationHealth;
import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.agent.IntegrationResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Shared ownership-safe filesystem mechanics for project-local native integrations. */
public final class NativeIntegrationSupport {
  private NativeIntegrationSupport() {}

  /** Returns whether both files of {@code plan} exist and carry the Ward ownership marker. */
  public static boolean installed(final Path projectRoot, final NativeArtifactPlan plan) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    return owned(root.resolve(plan.configPath()), plan.marker())
        && (!plan.hasHook() || owned(root.resolve(plan.hookPath()), plan.marker()));
  }

  /** Reports whether the files of {@code plan} are installed, missing, partial, or user-owned. */
  public static IntegrationHealth health(final Path projectRoot, final NativeArtifactPlan plan) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final List<Path> files = plan.files().stream().map(root::resolve).toList();
    final boolean anyExists = files.stream().anyMatch(Files::exists);
    final long owned = files.stream().filter(file -> owned(file, plan.marker())).count();
    final long existing = files.stream().filter(Files::exists).count();
    if (!anyExists) {
      return new IntegrationHealth(
          IntegrationHealth.Status.MISSING, "Integration is not configured.");
    }
    if (owned != existing) {
      return new IntegrationHealth(
          IntegrationHealth.Status.CONFLICT, "User-owned integration content requires review.");
    }
    if (owned == files.size()) {
      return new IntegrationHealth(IntegrationHealth.Status.INSTALLED, "Integration is installed.");
    }
    return new IntegrationHealth(
        IntegrationHealth.Status.PARTIAL, "Integration is partially installed; re-run integrate.");
  }

  /**
   * Writes or removes the files of {@code plan} as {@code request} says, never overwriting
   * user-owned files.
   */
  public static IntegrationResult integrate(
      final Path projectRoot, final IntegrationRequest request, final NativeArtifactPlan plan) {
    final NativeIntegrationTransaction transaction;
    try {
      transaction =
          NativeIntegrationTransaction.capture(
              Stream.concat(plan.files().stream(), plan.legacyPaths().stream())
                  .toArray(Path[]::new));
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Could not snapshot " + plan.id() + " integration: " + exception.getMessage(), exception);
    }
    try {
      final IntegrationResult conflict = firstConflict(plan);
      if (conflict != null) {
        return conflict.withCapabilities(plan.capabilities());
      }
      if (request.remove()) {
        return remove(plan);
      }
      return install(plan);
    } catch (IOException exception) {
      try {
        transaction.rollback();
      } catch (IOException rollbackException) {
        exception.addSuppressed(rollbackException);
      }
      throw new UncheckedIOException(
          "Could not update " + plan.id() + " integration: " + exception.getMessage(), exception);
    }
  }

  private static IntegrationResult install(final NativeArtifactPlan plan) throws IOException {
    final Path config = plan.configPath();
    final List<Map.Entry<Path, IntegrationResult>> results = new ArrayList<>();
    results.add(
        Map.entry(
            config,
            IntegrationArtifacts.installOrRefresh(
                config,
                plan.marker(),
                plan.configContent(),
                "Updated Ward " + plan.id() + " hooks.")));
    if (plan.hasHook()) {
      final Path hook = plan.hookPath();
      results.add(
          Map.entry(
              hook,
              IntegrationArtifacts.installOrRefresh(
                  hook,
                  plan.marker(),
                  plan.hookContent(),
                  "Updated Ward " + plan.id() + " hook.")));
      if (Files.exists(hook)) {
        IntegrationArtifacts.makeExecutable(hook);
      }
    }
    for (final Path legacy : plan.legacyPaths()) {
      for (final Path old : plan.legacy(legacy).delete()) {
        results.add(
            Map.entry(
                old,
                new IntegrationResult(
                    IntegrationResult.Status.REMOVED,
                    List.of(old.toString()),
                    "Removed the Ward-owned " + plan.id() + " file of an earlier version.")));
      }
    }
    return IntegrationArtifacts.combine(
            results,
            "Ward " + plan.id() + " integration is already present.",
            "Created Ward " + plan.id() + " native hook integration.")
        .withCapabilities(plan.capabilities());
  }

  private static IntegrationResult remove(final NativeArtifactPlan plan) throws IOException {
    final boolean legacyPresent = legacyPresent(plan);
    if (plan.files().stream().noneMatch(Files::exists) && !legacyPresent) {
      return new IntegrationResult(
              IntegrationResult.Status.NOT_FOUND,
              List.of(),
              "No Ward " + plan.id() + " integration found.")
          .withCapabilities(plan.capabilities());
    }
    final List<String> removed = new ArrayList<>();
    for (final Path file : plan.files()) {
      final IntegrationResult result = IntegrationArtifacts.remove(file, plan.marker(), "");
      if (result.status() == IntegrationResult.Status.CONFLICT) {
        return result.withCapabilities(plan.capabilities());
      }
      removed.addAll(result.changed());
    }
    for (final Path legacy : plan.legacyPaths()) {
      plan.legacy(legacy).delete().forEach(old -> removed.add(old.toString()));
    }
    return new IntegrationResult(
            IntegrationResult.Status.REMOVED,
            removed,
            "Removed Ward " + plan.id() + " integration.")
        .withCapabilities(plan.capabilities());
  }

  private static boolean legacyPresent(final NativeArtifactPlan plan) throws IOException {
    for (final Path legacy : plan.legacyPaths()) {
      if (!plan.legacy(legacy).owned().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private static IntegrationResult firstConflict(final NativeArtifactPlan plan) throws IOException {
    for (final Path file : plan.files()) {
      final IntegrationResult conflict = IntegrationArtifacts.preflight(file, plan.marker());
      if (conflict != null) {
        return conflict;
      }
    }
    return null;
  }

  private static boolean owned(final Path target, final String marker) {
    try {
      return IntegrationArtifacts.owned(target, marker);
    } catch (IOException exception) {
      return false;
    }
  }
}

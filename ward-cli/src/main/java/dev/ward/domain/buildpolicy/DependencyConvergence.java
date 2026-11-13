package dev.ward.domain.buildpolicy;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.ModuleDependencies;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code ward:build/dependency-convergence}: one version of each package in a module. It counts the
 * versions the module resolves and, where the source reports them (Maven), every version that a
 * dependency requested before mediation, in every scope, which is what Maven Enforcer's {@code
 * dependencyConvergence} counts.
 */
final class DependencyConvergence implements BuildPolicyRule {
  /** The number of versions from which a package no longer converges. */
  private static final int DIVERGENT = 2;

  @Override
  public List<BuildPolicyFinding> check(final BuildPolicyInput input) {
    return input.inventory().get().modules().stream()
        .flatMap(DependencyConvergence::module)
        .toList();
  }

  private static Stream<BuildPolicyFinding> module(final ModuleDependencies module) {
    return module.dependencies().stream()
        .collect(
            Collectors.groupingBy(
                dependency -> dependency.id().key(), LinkedHashMap::new, Collectors.toList()))
        .values()
        .stream()
        .flatMap(entries -> finding(entries).stream());
  }

  private static Optional<BuildPolicyFinding> finding(final List<Dependency> entries) {
    final Map<String, String> versions = new TreeMap<>();
    for (final Dependency entry : entries) {
      versions.merge(entry.id().version(), entry.source(), (first, second) -> first);
      entry
          .requested()
          .forEach(
              request ->
                  versions.merge(
                      request.version(),
                      request.requester().isEmpty() ? "the module" : request.requester(),
                      (first, second) -> first.compareTo(second) <= 0 ? first : second));
    }
    if (versions.size() < DIVERGENT) {
      return Optional.empty();
    }
    final Dependency first = entries.getFirst();
    final String listed =
        versions.entrySet().stream()
            .map(version -> version.getKey() + " (" + version.getValue() + ")")
            .collect(Collectors.joining(", "));
    return Optional.of(
        new BuildPolicyFinding(
            BuildRule.DEPENDENCY_CONVERGENCE,
            first.id().name() + " comes in " + versions.size() + " versions: " + listed + ".",
            first.source(),
            0));
  }
}

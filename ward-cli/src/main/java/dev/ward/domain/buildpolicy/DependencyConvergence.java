package dev.ward.domain.buildpolicy;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.ModuleDependencies;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** {@code ward:build/dependency-convergence}: one version of each package in a module. */
final class DependencyConvergence implements BuildPolicyRule {
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
        .filter(entries -> entries.size() > 1)
        .map(DependencyConvergence::finding);
  }

  private static BuildPolicyFinding finding(final List<Dependency> entries) {
    final String versions =
        entries.stream()
            .map(entry -> entry.id().version() + " (" + entry.source() + ")")
            .collect(Collectors.joining(", "));
    final Dependency first = entries.getFirst();
    return new BuildPolicyFinding(
        BuildRule.DEPENDENCY_CONVERGENCE,
        first.id().name() + " resolves to " + entries.size() + " versions: " + versions + ".",
        first.source(),
        0);
  }
}

package dev.ward.domain.buildpolicy;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.RequestedVersion;
import dev.ward.domain.vulnerability.VersionOrder;
import dev.ward.domain.vulnerability.maven.MavenVersionOrder;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code ward:build/require-upper-bound}: a Maven package that resolves to a lower version than one
 * a dependency asks for, which is what Maven Enforcer's {@code requireUpperBoundDeps} reports. It
 * reads the versions requested before Maven mediated them, in every scope, and orders them the way
 * Maven does. A source that reports no requests gives no findings.
 */
final class RequireUpperBound implements BuildPolicyRule {
  private static final VersionOrder ORDER = new MavenVersionOrder();

  @Override
  public List<BuildPolicyFinding> check(final BuildPolicyInput input) {
    return input.inventory().get().modules().stream().flatMap(RequireUpperBound::module).toList();
  }

  private static Stream<BuildPolicyFinding> module(final ModuleDependencies module) {
    return module.dependencies().stream()
        .filter(dependency -> dependency.id().ecosystem() == Ecosystem.MAVEN)
        .flatMap(RequireUpperBound::finding);
  }

  private static Stream<BuildPolicyFinding> finding(final Dependency dependency) {
    final String resolved = dependency.id().version();
    final List<RequestedVersion> higher =
        dependency.requested().stream()
            .filter(request -> ORDER.compare(resolved, request.version()) < 0)
            .sorted(
                Comparator.comparing(RequestedVersion::version, ORDER::compare)
                    .thenComparing(RequestedVersion::requester))
            .toList();
    if (higher.isEmpty()) {
      return Stream.empty();
    }
    final String asked =
        higher.stream()
            .map(request -> request.version() + " (" + requester(request) + ")")
            .collect(Collectors.joining(", "));
    return Stream.of(
        new BuildPolicyFinding(
            BuildRule.REQUIRE_UPPER_BOUND,
            dependency.id().name()
                + " resolves to "
                + resolved
                + " but a higher version is requested: "
                + asked
                + ".",
            dependency.source(),
            0));
  }

  private static String requester(final RequestedVersion request) {
    return request.requester().isEmpty() ? "the module" : request.requester();
  }
}

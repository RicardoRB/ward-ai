package dev.ward.domain.buildpolicy;

import java.util.List;
import java.util.Optional;

/** {@code ward:build/no-dynamic-version}: no version that can resolve differently later. */
final class NoDynamicVersion implements BuildPolicyRule {
  @Override
  public List<BuildPolicyFinding> check(final BuildPolicyInput input) {
    return input.model().declarations().stream()
        .filter(Declaration::versioned)
        .filter(declaration -> declaration.flag() != Declaration.Flag.UNDETERMINABLE)
        .map(NoDynamicVersion::violation)
        .flatMap(Optional::stream)
        .toList();
  }

  private static Optional<BuildPolicyFinding> violation(final Declaration declaration) {
    final Dynamism dynamism =
        Versions.dynamism(declaration.ecosystem(), declaration.kind(), declaration.version());
    final boolean pinned =
        dynamism == Dynamism.RANGE && declaration.flag() == Declaration.Flag.LOCKED;
    if (dynamism == Dynamism.NONE || pinned) {
      return Optional.empty();
    }
    return Optional.of(
        new BuildPolicyFinding(
            BuildRule.NO_DYNAMIC_VERSION,
            declaration.name()
                + " has the dynamic version "
                + declaration.version()
                + "; use an exact version"
                + (dynamism == Dynamism.RANGE ? " or commit a lockfile." : "."),
            declaration.file(),
            declaration.line()));
  }
}

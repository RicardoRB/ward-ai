package dev.ward.infrastructure.dependency.node;

import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.PackageId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What a Node lockfile says, whatever its format: every locked package with the packages it
 * requires, and how a workspace member's declared dependency resolves.
 *
 * @param packages the locked packages by identity; a workspace member is not one of them
 * @param directs resolves a dependency name declared by the member at a path (empty for the root)
 */
record NodeLock(Map<PackageId, Locked> packages, NodeDirects directs) {
  /** A locked package. */
  record Locked(boolean released, Set<PackageId> requires) {}

  /** Resolves what a member's manifest declares to a locked package. */
  @FunctionalInterface
  interface NodeDirects {
    /**
     * Returns the package the member resolves {@code name} to, or empty when the lock has none.
     *
     * @param range the requirement the manifest declares, which Yarn matches stanzas by
     */
    Optional<PackageId> resolve(String memberPath, String name, String range);
  }

  /** Copies the packages. */
  NodeLock {
    packages = Map.copyOf(packages);
  }

  /** Collects locked packages, merging the entries of one identity. */
  static final class Collector {
    private final Map<PackageId, Boolean> released = new HashMap<>();
    private final Map<PackageId, Set<PackageId>> requires = new HashMap<>();

    /** Adds a package once per place it is locked at. */
    void add(final PackageId id, final boolean isReleased, final Set<PackageId> required) {
      released.merge(id, isReleased, Boolean::logicalAnd);
      requires.computeIfAbsent(id, key -> new HashSet<>()).addAll(required);
    }

    /** Returns the collected packages. */
    Map<PackageId, Locked> packages() {
      final Map<PackageId, Locked> result = new HashMap<>();
      released.forEach((id, flag) -> result.put(id, new Locked(flag, requires.get(id))));
      return result;
    }
  }

  /** Returns an npm package identity. */
  static PackageId npm(final String name, final String version) {
    return new PackageId(Ecosystem.NPM, name, version);
  }

  /** Returns whether {@code resolved} is a registry tarball, which is a published release. */
  static boolean registryTarball(final String resolved) {
    return resolved.startsWith("http") && resolved.contains("/-/");
  }
}

package dev.ward.infrastructure.dependency.python;

import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.PackageId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a uv or Poetry lockfile says: the locked packages with what they require, and for each
 * module the packages each declared name resolves to.
 *
 * @param packages the locked packages by identity; workspace members and local projects without a
 *     version are not packages
 * @param directs by module name (empty for Poetry) and normalized dependency name
 */
record PythonLock(Map<PackageId, Locked> packages, Map<String, Map<String, Resolved>> directs) {
  /** A locked package. */
  record Locked(boolean released, Set<PackageId> requires) {}

  /**
   * The packages a declared name resolves to.
   *
   * @param ids the packages; for a workspace member, the packages that member resolves
   * @param member whether the name is a workspace member rather than a package
   */
  record Resolved(List<PackageId> ids, boolean member) {}

  /** Copies the maps. */
  PythonLock {
    packages = Map.copyOf(packages);
    directs = Map.copyOf(directs);
  }

  /** Returns a PyPI package identity. */
  static PackageId pypi(final String name, final String version) {
    return new PackageId(Ecosystem.PYPI, name, version);
  }

  /** Collects locked packages, merging the entries of one identity. */
  static final class Collector {
    private final Map<PackageId, Boolean> released = new HashMap<>();
    private final Map<PackageId, Set<PackageId>> requires = new HashMap<>();

    /** Adds a package. */
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
}

package dev.ward.domain.dependency;

import java.util.Set;

/**
 * One resolved package of a module: the contract that vulnerability, license, SBOM and build-policy
 * checks read.
 *
 * @param id the package identity: ecosystem, canonical name and exact version
 * @param relation {@code DIRECT} when the module's manifest declares it; a package that is both
 *     declared and required is {@code DIRECT}
 * @param scope the widest scope of the paths that reach the package
 * @param released {@code true} for a published release from the ecosystem's usual source, {@code
 *     false} for a Git revision or branch, a URL or a local path, which advisories for registry
 *     versions must not be matched against
 * @param source the project-relative path, with {@code /}, of the lockfile or build file the entry
 *     was read from; never empty
 * @param declaredIn the project-relative path of the manifest or build file that declares a direct
 *     entry, empty for a transitive one
 * @param requiredBy the packages that depend on it; empty when the source records no edges, which
 *     never means that the package is direct
 * @param requested the versions asked for this package's name before the build tool mediated them,
 *     one per requester, including the resolved one. Only sources that report the unmediated tree
 *     fill it (Maven); it is empty otherwise, which means "not reported", never "nobody asked". It
 *     is what dependency convergence and upper-bound checks compare: a package is divergent when
 *     {@code requested} holds more than one distinct version
 */
public record Dependency(
    PackageId id,
    DependencyRelation relation,
    DependencyScope scope,
    boolean released,
    String source,
    String declaredIn,
    Set<PackageId> requiredBy,
    Set<RequestedVersion> requested) {
  /**
   * Copies the sets and checks that a source is named.
   *
   * @throws IllegalArgumentException if {@code source} is blank
   */
  public Dependency {
    if (source.isBlank()) {
      throw new IllegalArgumentException("A dependency must name its source file");
    }
    requiredBy = Set.copyOf(requiredBy);
    requested = Set.copyOf(requested);
  }

  /** Returns whether the module's manifest declares this package. */
  public boolean direct() {
    return relation == DependencyRelation.DIRECT;
  }

  /** Returns the Package URL of this entry. */
  public String purl() {
    return id.purl();
  }
}

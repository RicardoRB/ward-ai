package dev.ward.domain.dependency;

/**
 * A version that was requested for a package before the build tool mediated the versions.
 *
 * @param requester the Package URL of the package that asked for it, or an empty text when the
 *     module's own manifest asked for it
 * @param version the requested version as written: an exact version, a range or a property-free
 *     soft requirement; never parsed. When the build tool's dependency management overrode it, the
 *     version it was overridden to, which is what the tree holds
 * @param premanaged the version the requester wrote before dependency management overrode it, or an
 *     empty text when nothing overrode it
 */
public record RequestedVersion(String requester, String version, String premanaged) {
  /** Creates a request that dependency management did not change. */
  public RequestedVersion(final String requester, final String version) {
    this(requester, version, "");
  }

  /** Returns the version the requester wrote: {@link #premanaged} when set, else the version. */
  public String written() {
    return premanaged.isEmpty() ? version : premanaged;
  }

  /** Creates the request of the module's own manifest. */
  public static RequestedVersion byModule(final String version) {
    return new RequestedVersion("", version);
  }

  /** Creates the request of {@code requester}. */
  public static RequestedVersion by(final PackageId requester, final String version) {
    return new RequestedVersion(requester.purl(), version);
  }
}

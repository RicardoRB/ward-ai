package dev.ward.domain.dependency;

/**
 * A version that was requested for a package before the build tool mediated the versions.
 *
 * @param requester the Package URL of the package that asked for it, or an empty text when the
 *     module's own manifest asked for it
 * @param version the requested version as written: an exact version, a range or a property-free
 *     soft requirement; never parsed
 */
public record RequestedVersion(String requester, String version) {
  /** Creates the request of the module's own manifest. */
  public static RequestedVersion byModule(final String version) {
    return new RequestedVersion("", version);
  }

  /** Creates the request of {@code requester}. */
  public static RequestedVersion by(final PackageId requester, final String version) {
    return new RequestedVersion(requester.purl(), version);
  }
}

package dev.ward.domain.license;

/**
 * What a {@link LicenseMetadata} reader found for one package: a declared license, or why there is
 * none.
 *
 * @param license the declared license, or {@code null} when none was found
 * @param reason why no license was found, or {@code null} when one was
 * @param detail a short note for reports: the source read, or what is missing; never file contents
 */
public record LicenseLookup(DeclaredLicense license, Reason reason, String detail) {

  /** Why a package has no declared license. */
  public enum Reason {
    /** No metadata of the package exists locally. */
    NOT_INSTALLED,
    /** Metadata exists but for another version than the inventory's. */
    VERSION_MISMATCH,
    /** The metadata declares no license. */
    NO_LICENSE_DECLARED,
    /** A license text exists that Ward cannot match to a license. */
    UNRECOGNIZED
  }

  /** Returns the lookup of a package whose metadata declares {@code license}. */
  public static LicenseLookup found(final DeclaredLicense license, final String source) {
    return new LicenseLookup(license, null, source);
  }

  /** Returns the lookup of a package without a declared license. */
  public static LicenseLookup missing(final Reason reason, final String detail) {
    return new LicenseLookup(null, reason, detail);
  }

  /** Returns whether a license was found. */
  public boolean present() {
    return license != null;
  }
}

package dev.ward.application.license;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.license.DeclaredLicense;
import dev.ward.domain.license.LicenseExpression;
import dev.ward.domain.license.LicenseLookup;
import java.util.Optional;

/**
 * The license of one package of the inventory.
 *
 * @param dependency the inventory entry of the package
 * @param lookup what the metadata reader found
 * @param expression the normalized SPDX expression, or empty when the license is unknown
 */
public record PackageLicense(
    Dependency dependency, LicenseLookup lookup, Optional<LicenseExpression> expression) {

  /** Returns whether the package has a normalized license. */
  public boolean known() {
    return expression.isPresent();
  }

  /** Returns the declared text for reports, or empty when none was found. */
  public String declared() {
    return lookup.present() ? lookup.license().describe() : "";
  }

  /** Returns the declared license, or empty when none was found. */
  public Optional<DeclaredLicense> declaredLicense() {
    return Optional.ofNullable(lookup.license());
  }

  /** Returns why the license is unknown, for the message of a finding. */
  public String unknownReason() {
    if (!lookup.present()) {
      return lookup.detail();
    }
    return "the declared license \"" + declared() + "\" is not an SPDX license Ward recognizes";
  }
}

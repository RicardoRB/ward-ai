package dev.ward.domain.license;

import java.util.Optional;
import java.util.SortedSet;

/** Port for the license texts that Ward bundles. */
public interface LicenseTexts {
  /** Returns the full text of the license {@code id}, if Ward bundles it. */
  Optional<String> text(String id);

  /** Returns the identifiers that have a bundled text, ordered. */
  SortedSet<String> ids();
}

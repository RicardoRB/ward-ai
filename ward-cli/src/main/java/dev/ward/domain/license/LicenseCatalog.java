package dev.ward.domain.license;

import java.util.Optional;

/** Port for the bundled SPDX data: identifiers, name and URL aliases, and Python classifiers. */
public interface LicenseCatalog {
  /**
   * Returns the current SPDX identifier for {@code id}, compared case-insensitively, with a
   * deprecated identifier replaced ({@code GPL-2.0} gives {@code GPL-2.0-only}); empty for an
   * identifier the data does not list.
   */
  Optional<String> id(String id);

  /** Returns the identifier of a license name or URL spelling, compared after normalization. */
  Optional<String> alias(String nameOrUrl);

  /** Returns the identifier of a {@code License ::} trove classifier. */
  Optional<String> classifier(String classifier);
}

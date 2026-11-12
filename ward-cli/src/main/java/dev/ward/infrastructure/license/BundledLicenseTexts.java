package dev.ward.infrastructure.license;

import dev.ward.domain.license.LicenseTexts;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.inject.Inject;

/**
 * The license texts bundled under {@code dev/ward/license/texts}, listed in {@code index.txt}. A
 * file named after a family serves its {@code -only} and {@code -or-later} identifiers too, since
 * both share one text.
 */
public final class BundledLicenseTexts implements LicenseTexts {
  private static final String DIRECTORY = "/dev/ward/license/texts/";
  private static final String ONLY = "-only";
  private static final String OR_LATER = "-or-later";

  private final SortedSet<String> ids = new TreeSet<>();

  /** Reads the index of the bundled texts. */
  @Inject
  public BundledLicenseTexts() {
    for (final String name : read("index.txt").orElse("").split("\\R")) {
      if (name.endsWith(".txt")) {
        ids.add(name.substring(0, name.length() - ".txt".length()));
      }
    }
  }

  @Override
  public Optional<String> text(final String id) {
    final String file = ids.contains(id) ? id : family(id);
    return ids.contains(file) ? read(file + ".txt") : Optional.empty();
  }

  @Override
  public SortedSet<String> ids() {
    return new TreeSet<>(ids);
  }

  private static String family(final String id) {
    if (id.endsWith(ONLY)) {
      return id.substring(0, id.length() - ONLY.length());
    }
    return id.endsWith(OR_LATER) ? id.substring(0, id.length() - OR_LATER.length()) : id;
  }

  private static Optional<String> read(final String file) {
    try (InputStream stream = BundledLicenseTexts.class.getResourceAsStream(DIRECTORY + file)) {
      return stream == null
          ? Optional.empty()
          : Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read bundled license text " + file, exception);
    }
  }
}

package dev.ward.infrastructure.license;

import dev.ward.domain.license.LicenseCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.inject.Inject;

/**
 * The SPDX data that Ward bundles under {@code dev/ward/license}: {@code spdx-ids.txt}, {@code
 * license-aliases.txt} and {@code classifiers.txt}. Reads the files once, from the class path.
 */
public final class BundledLicenseCatalog implements LicenseCatalog {
  private static final String DIRECTORY = "/dev/ward/license/";
  private static final Pattern SCHEME = Pattern.compile("^https?://(www\\.)?");
  private static final Pattern SPACES = Pattern.compile("\\s+");

  private final Map<String, String> ids = new HashMap<>();
  private final Map<String, String> aliases = new HashMap<>();
  private final Map<String, String> classifiers = new HashMap<>();

  /** Reads the bundled data. */
  @Inject
  public BundledLicenseCatalog() {
    for (final String line : lines("spdx-ids.txt")) {
      final int bar = line.indexOf('|');
      final String id = bar < 0 ? line : line.substring(0, bar);
      ids.put(id.toLowerCase(Locale.ROOT), bar < 0 ? id : line.substring(bar + 1));
    }
    pairs("license-aliases.txt", aliases);
    pairs("classifiers.txt", classifiers);
  }

  @Override
  public Optional<String> id(final String id) {
    return Optional.ofNullable(ids.get(id.trim().toLowerCase(Locale.ROOT)));
  }

  @Override
  public Optional<String> alias(final String nameOrUrl) {
    return Optional.ofNullable(aliases.get(key(nameOrUrl)));
  }

  @Override
  public Optional<String> classifier(final String classifier) {
    return Optional.ofNullable(classifiers.get(key(classifier)));
  }

  /** Normalizes a license name or URL: lower case, one space, no scheme, no trailing slash. */
  static String key(final String text) {
    final String lower = text.trim().toLowerCase(Locale.ROOT);
    final String plain = SPACES.matcher(SCHEME.matcher(lower).replaceFirst("")).replaceAll(" ");
    return plain.endsWith("/") ? plain.substring(0, plain.length() - 1) : plain;
  }

  /** Returns the identifiers of the bundled list, as written, for contract tests. */
  List<String> identifiers() {
    return List.copyOf(ids.values());
  }

  /** Returns the alias targets, for contract tests. */
  List<String> aliasTargets() {
    return List.copyOf(aliases.values());
  }

  /** Returns the classifier targets, for contract tests. */
  List<String> classifierTargets() {
    return List.copyOf(classifiers.values());
  }

  private static void pairs(final String file, final Map<String, String> target) {
    for (final String line : lines(file)) {
      final int equals = line.lastIndexOf('=');
      if (equals > 0) {
        target.put(key(line.substring(0, equals)), line.substring(equals + 1));
      }
    }
  }

  private static List<String> lines(final String file) {
    try (InputStream stream = BundledLicenseCatalog.class.getResourceAsStream(DIRECTORY + file)) {
      if (stream == null) {
        throw new IllegalStateException("Missing bundled resource " + file);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
          .lines()
          .filter(line -> !line.isBlank() && !line.startsWith("#"))
          .toList();
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read bundled resource " + file, exception);
    }
  }
}

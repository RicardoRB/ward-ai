package dev.ward.domain.dependency;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;

/**
 * The identity of a resolved package.
 *
 * <p>Equal packages have equal names: Maven {@code group:artifact} without classifier or type, npm
 * name with its scope ({@code @types/node}), PyPI name normalized by PEP 503 ({@code
 * django-rest-framework}), and SwiftPM the cleaned repository location without scheme, user, {@code
 * .git} or trailing slash, in lower case ({@code github.com/apple/swift-nio}), which is the name
 * OSV gives SwiftPM packages. The version is the exact text the source wrote.
 *
 * @param ecosystem the ecosystem
 * @param name the canonical package name
 * @param version the exact resolved version; for a Swift pin without a version, its revision
 */
public record PackageId(Ecosystem ecosystem, String name, String version)
    implements Comparable<PackageId> {
  private static final int BYTE_MASK = 0xFF;
  private static final String UNRESERVED =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
  private static final Comparator<PackageId> ORDER =
      Comparator.comparing(PackageId::ecosystem)
          .thenComparing(PackageId::name)
          .thenComparing(PackageId::version);

  /**
   * Validates the parts.
   *
   * @throws IllegalArgumentException if the name or version is blank
   */
  public PackageId {
    if (name.isBlank() || version.isBlank()) {
      throw new IllegalArgumentException("A package needs a name and an exact version");
    }
  }

  @Override
  public int compareTo(final PackageId other) {
    return ORDER.compare(this, other);
  }

  /** Returns the package name without its version: the ecosystem and the name. */
  public String key() {
    return ecosystem.purlType() + ":" + name;
  }

  /** Returns the Package URL of this package, such as {@code pkg:npm/%40types/node@22.10.1}. */
  public String purl() {
    return "pkg:" + ecosystem.purlType() + "/" + purlName() + "@" + encode(version);
  }

  private String purlName() {
    return switch (ecosystem) {
      case MAVEN -> name.replace(':', '/');
      case NPM -> encodeSegments(name);
      case PYPI, SWIFT_URL -> name;
      default -> name;
    };
  }

  private static String encodeSegments(final String name) {
    final int slash = name.indexOf('/');
    return slash < 0
        ? encode(name)
        : encode(name.substring(0, slash)) + "/" + encode(name.substring(slash + 1));
  }

  /** Percent-encodes every UTF-8 byte except the unreserved characters of RFC 3986. */
  private static String encode(final String text) {
    final StringBuilder encoded = new StringBuilder();
    for (final byte value : text.getBytes(StandardCharsets.UTF_8)) {
      final char character = (char) (value & BYTE_MASK);
      if (UNRESERVED.indexOf(character) >= 0) {
        encoded.append(character);
      } else {
        encoded.append('%').append(String.format("%02X", value & BYTE_MASK));
      }
    }
    return encoded.toString();
  }
}

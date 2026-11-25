package dev.ward.analysis.domain.python;

import java.util.Set;

/**
 * What a call does with a digest that it takes as an argument, told by the callee as written. A
 * path function only names a file with it, an authentication function judges it, and a conversion
 * hands it on in another form.
 */
enum DigestCallees {
  /** The call authenticates with the digest. */
  SECURITY,
  /** The call only names or finds a file or a path with the digest. */
  BENIGN,
  /** The call returns the digest in another form. */
  CARRIES,
  /** The analysis does not follow the digest into the call. */
  OTHER;

  private static final Set<String> PATHS =
      Set.of(
          "open",
          "os.path.join",
          "os.path.exists",
          "os.path.isfile",
          "os.path.isdir",
          "os.makedirs",
          "os.mkdir",
          "os.rename",
          "os.replace",
          "os.remove",
          "os.unlink",
          "shutil.copy",
          "shutil.copyfile",
          "shutil.move",
          "Path",
          "PurePath",
          "pathlib.Path",
          "pathlib.PurePath"
      );
  private static final Set<String> CONVERSIONS = Set.of("str", "bytes", "bytearray", "repr");
  private static final String COMPARE = "compare_digest";
  private static final String HMAC = "hmac.";

  /** Classifies the callee written as {@code name}, such as {@code os.path.join}. */
  static DigestCallees of(final String name) {
    if (name.equals(COMPARE) || name.endsWith("." + COMPARE) || name.startsWith(HMAC)) {
      return SECURITY;
    }
    if (PATHS.contains(name)) {
      return BENIGN;
    }
    return CONVERSIONS.contains(name) || name.endsWith(".format") || name.endsWith(".join")
        ? CARRIES
        : OTHER;
  }
}

package dev.ward.infrastructure.dependency.swift;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reduces a SwiftPM repository location to the name OSV gives the package. */
final class SwiftLocations {
  private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*://");
  private static final Pattern SCP_LIKE = Pattern.compile("^[^@/:]+@([^:/]+):(.*)$");
  private static final Pattern USER = Pattern.compile("^[^/@]*@");
  private static final String GIT_SUFFIX = ".git";

  private SwiftLocations() {}

  /**
   * Returns {@code location} without scheme, user information, {@code .git} and trailing slashes,
   * in lower case: {@code https://user@GitHub.com/Apple/Swift-NIO.git/} becomes {@code
   * github.com/apple/swift-nio}.
   */
  static String normalize(final String location) {
    String text = location.strip();
    final Matcher scp = SCP_LIKE.matcher(text);
    if (!SCHEME.matcher(text).find() && scp.matches()) {
      text = scp.group(1) + "/" + scp.group(2);
    }
    text = SCHEME.matcher(text).replaceFirst("");
    text = USER.matcher(text).replaceFirst("");
    text = text.toLowerCase(Locale.ROOT);
    text = stripSlashes(text);
    if (text.endsWith(GIT_SUFFIX)) {
      text = text.substring(0, text.length() - GIT_SUFFIX.length());
    }
    return stripSlashes(text);
  }

  private static String stripSlashes(final String text) {
    int end = text.length();
    while (end > 0 && text.charAt(end - 1) == '/') {
      end--;
    }
    return text.substring(0, end);
  }
}

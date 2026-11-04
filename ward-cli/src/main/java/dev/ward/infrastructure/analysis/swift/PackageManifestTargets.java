package dev.ward.infrastructure.analysis.swift;

import dev.ward.analysis.domain.swift.SwiftTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the targets of a {@code Package.swift} as text, without running the manifest. A manifest
 * whose targets are computed (a loop, a variable) is unreadable, so the caller falls back.
 */
final class PackageManifestTargets {

  // shortcut: a computed manifest is unreadable and falls back to the implicit module, upgrade with
  // `swift package dump-package`
  private static final Pattern TARGET_CALL =
      Pattern.compile("\\.(target|executableTarget|testTarget|macro)\\(");
  private static final Pattern NAME = Pattern.compile("name:\\s*\"([^\"]+)\"");
  private static final Pattern PATH = Pattern.compile("path:\\s*\"([^\"]+)\"");
  private static final Pattern DEPENDENCIES = Pattern.compile("dependencies:\\s*\\[([^\\]]*)\\]");
  private static final Pattern PRODUCT = Pattern.compile("\\.product\\([^)]*\\)");
  private static final Pattern STRING = Pattern.compile("\"([^\"]+)\"");
  private static final Pattern COMPUTED =
      Pattern.compile("\\bfor\\s+\\w+\\s+in\\b|\\+=|\\.map\\b|\\.append\\(|\\.filter\\b");
  private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");
  private static final String TEST_TARGET = "testTarget";
  private static final String TEST_DIRECTORY = "Tests/";
  private static final String SOURCE_DIRECTORY = "Sources/";
  private static final String CURRENT = "./";
  private static final char OPEN = '(';
  private static final char CLOSE = ')';

  private PackageManifestTargets() {}

  /**
   * Returns the targets the manifest declares, or an empty result if the manifest is unreadable.
   */
  static Optional<List<SwiftTarget>> read(final String manifest) {
    final String text = LINE_COMMENT.matcher(manifest).replaceAll("");
    if (COMPUTED.matcher(text).find() || !literalTargetList(text)) {
      return Optional.empty();
    }
    final List<SwiftTarget> targets = new ArrayList<>();
    final Matcher calls = TARGET_CALL.matcher(text);
    int from = 0;
    while (calls.find(from)) {
      final String arguments = argumentsFrom(text, calls.end());
      final Optional<SwiftTarget> target = targetOf(calls.group(1), arguments);
      if (target.isEmpty()) {
        return Optional.empty();
      }
      targets.add(target.get());
      // a target named inside another target's dependencies is not a declaration of its own
      from = calls.end() + arguments.length();
    }
    return Optional.of(targets);
  }

  private static boolean literalTargetList(final String text) {
    final int declared = text.indexOf("targets:");
    return declared < 0 || text.substring(declared).matches("(?s)targets:\\s*\\[.*");
  }

  private static Optional<SwiftTarget> targetOf(final String kind, final String arguments) {
    final Matcher name = NAME.matcher(arguments);
    if (!name.find()) {
      return Optional.empty();
    }
    final Matcher path = PATH.matcher(arguments);
    final String directory =
        path.find() ? normalized(path.group(1)) : defaultDirectory(kind, name.group(1));
    return Optional.of(
        new SwiftTarget(name.group(1), List.of(directory), List.of(), dependenciesOf(arguments)));
  }

  private static String defaultDirectory(final String kind, final String name) {
    return (TEST_TARGET.equals(kind) ? TEST_DIRECTORY : SOURCE_DIRECTORY) + name;
  }

  private static List<String> dependenciesOf(final String arguments) {
    final List<String> dependencies = new ArrayList<>();
    final Matcher list = DEPENDENCIES.matcher(PRODUCT.matcher(arguments).replaceAll(""));
    if (list.find()) {
      final Matcher literal = STRING.matcher(list.group(1));
      while (literal.find()) {
        dependencies.add(literal.group(1));
      }
    }
    return dependencies;
  }

  /** The text between an opening parenthesis and its matching closing one. */
  private static String argumentsFrom(final String text, final int start) {
    int depth = 1;
    int index = start;
    while (index < text.length() && depth > 0) {
      final char current = text.charAt(index);
      if (current == OPEN) {
        depth++;
      } else if (current == CLOSE) {
        depth--;
      }
      index++;
    }
    return text.substring(start, index);
  }

  private static String normalized(final String path) {
    final String trimmed = path.startsWith(CURRENT) ? path.substring(CURRENT.length()) : path;
    return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
  }
}

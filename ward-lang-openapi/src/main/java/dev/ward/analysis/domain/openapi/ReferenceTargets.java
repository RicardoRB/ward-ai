package dev.ward.analysis.domain.openapi;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The text rules of {@code $ref} values: where they point and how a pointer is followed. */
final class ReferenceTargets {

  private static final String PARENT = "..";
  private static final List<String> SUPPORTED = List.of("3.0", "3.1");

  private ReferenceTargets() {}

  /** Returns why a reference value can never be used, or empty when it may be tried. */
  static Optional<String> problem(final String reference) {
    final String lower = reference.toLowerCase(Locale.ROOT);
    if (lower.startsWith("http://") || lower.startsWith("https://")) {
      return Optional.of(
          "the reference '" + reference + "' is remote; Ward does not fetch remote references");
    }
    final int colon = reference.indexOf(':');
    final int hash = reference.indexOf('#');
    if (colon >= 0 && (hash < 0 || colon < hash)) {
      return Optional.of("the reference '" + reference + "' uses an unsupported scheme");
    }
    return Optional.empty();
  }

  /**
   * Returns the project path of the file part of a reference made in {@code from}: relative to the
   * directory of {@code from}, normalized; empty when it leaves the project root.
   */
  static Optional<String> path(final String from, final String filePart) {
    if (filePart.isEmpty()) {
      return Optional.of(from);
    }
    if (filePart.startsWith("/")) {
      return Optional.empty();
    }
    final int slash = from.lastIndexOf('/');
    final String directory = slash < 0 ? "" : from.substring(0, slash);
    return normalize((directory + "/" + filePart).split("/"));
  }

  private static Optional<String> normalize(final String... parts) {
    final Deque<String> segments = new ArrayDeque<>();
    for (final String segment : parts) {
      if (PARENT.equals(segment)) {
        if (segments.isEmpty()) {
          return Optional.empty();
        }
        segments.removeLast();
      } else if (!segment.isEmpty() && !".".equals(segment)) {
        segments.addLast(segment);
      }
    }
    return segments.isEmpty() ? Optional.empty() : Optional.of(String.join("/", segments));
  }

  /** Follows a JSON pointer ({@code /a/b/0}, empty for the root) from {@code root}. */
  static Optional<DocNode> pointer(final DocNode root, final String pointer) {
    if (pointer.isEmpty() || "/".equals(pointer)) {
      return Optional.of(root);
    }
    if (!pointer.startsWith("/")) {
      return Optional.empty();
    }
    Optional<DocNode> current = Optional.of(root);
    for (final String raw : pointer.substring(1).split("/", -1)) {
      final String token = raw.replace("~1", "/").replace("~0", "~");
      current = current.flatMap(node -> step(node, token));
    }
    return current;
  }

  private static Optional<DocNode> step(final DocNode node, final String token) {
    if (node.kind() == DocNode.Kind.OBJECT) {
      return node.get(token);
    }
    if (node.kind() == DocNode.Kind.ARRAY && token.matches("0|[1-9][0-9]{0,8}")) {
      return node.at(Integer.parseInt(token));
    }
    return Optional.empty();
  }

  /** Tells whether an {@code openapi} version text is 3.0 or 3.1, with or without a patch. */
  static boolean supportedVersion(final String version) {
    return SUPPORTED.stream()
        .anyMatch(major -> major.equals(version) || version.startsWith(major + "."));
  }

  /** Returns the {@code $ref} objects of a file, in file order. */
  static List<DocNode> referencesIn(final DocNode root) {
    final List<DocNode> found = new ArrayList<>();
    final Deque<DocNode> stack = new ArrayDeque<>();
    stack.push(root);
    while (!stack.isEmpty()) {
      final DocNode node = stack.pop();
      if (node.reference().isPresent()) {
        found.add(node);
      }
      final List<DocNode> children = node.children();
      for (int index = children.size() - 1; index >= 0; index--) {
        stack.push(children.get(index));
      }
    }
    return found;
  }

  /** Decodes the percent escapes of a file part or fragment. */
  static String decode(final String text) {
    return text.indexOf('%') < 0
        ? text
        : URLDecoder.decode(text.replace("+", "%2B"), StandardCharsets.UTF_8);
  }
}

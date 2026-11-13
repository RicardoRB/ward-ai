package dev.ward.infrastructure.dependency.maven;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One node of the verbose TGF tree: {@code g:a:type[:classifier]:version[:scope]}, written in
 * parentheses with a reason when Maven omitted it.
 *
 * @param name {@code groupId:artifactId}, without classifier
 * @param version the version the node's parent requested, or the module's own version
 * @param scope the Maven scope, empty for the module itself
 * @param omitted whether Maven left it out for a conflict, a duplicate or a cycle
 * @param cycle whether it was omitted for a cycle
 * @param premanaged the version the parent wrote when {@code dependencyManagement} overrode it
 *     ({@code version managed from X}), or an empty text
 */
record TgfNode(
    String name, String version, String scope, boolean omitted, boolean cycle, String premanaged) {
  private static final int MIN_COORDINATE_PARTS = 5;
  private static final String REASON_SEPARATOR = " - ";
  private static final Pattern MANAGED = Pattern.compile("version managed from ([^;)\\s]+)");

  /** Returns the version in {@code version managed from X}, or an empty text. */
  private static String managedFrom(final String text) {
    final Matcher matcher = MANAGED.matcher(text);
    return matcher.find() ? matcher.group(1) : "";
  }

  /** Parses the label of a node. */
  static TgfNode parse(final String text) {
    final boolean omitted = text.startsWith("(");
    final int reasonAt = text.indexOf(REASON_SEPARATOR);
    final String body = omitted ? text.substring(1, reasonAt) : text;
    final String reason = omitted ? text.substring(reasonAt) : "";
    final String[] parts = body.split(":");
    final boolean hasScope = parts.length >= MIN_COORDINATE_PARTS;
    return new TgfNode(
        parts[0] + ":" + parts[1],
        parts[parts.length - (hasScope ? 2 : 1)],
        hasScope ? parts[parts.length - 1] : "",
        omitted,
        reason.contains("cycle"),
        managedFrom(text));
  }
}

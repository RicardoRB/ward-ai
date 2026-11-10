package dev.ward.analysis.domain.node;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The names that {@code /* global *&#47;} and {@code /* globals *&#47;} comments declare, read as
 * ESLint reads them: a comma-separated list, each name optionally followed by {@code :} and a
 * value, and a name whose value is {@code off} is not declared. A {@code --} starts a description
 * that is not part of the list.
 */
final class NodeGlobalComments {

  private static final Pattern COMMENT =
      Pattern.compile("/\\*\\s*globals?\\s(.*?)\\*/", Pattern.DOTALL);
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");
  private static final String DESCRIPTION = "--";
  private static final String OFF = "off";

  private NodeGlobalComments() {}

  /** The names a source declares as globals, in source order; a name set to off is left out. */
  static Set<String> of(final String text) {
    final Set<String> names = new LinkedHashSet<>();
    final Matcher comment = COMMENT.matcher(text);
    while (comment.find()) {
      Arrays.stream(list(comment.group(1))).forEach(item -> declare(item, names));
    }
    return Set.copyOf(names);
  }

  private static String[] list(final String body) {
    final int description = body.indexOf(DESCRIPTION);
    return (description < 0 ? body : body.substring(0, description)).split(",");
  }

  private static void declare(final String item, final Set<String> names) {
    final String[] parts = item.split(":", 2);
    final String name = parts[0].strip();
    final boolean off = parts.length == 2 && OFF.equals(parts[1].strip());
    if (!off && IDENTIFIER.matcher(name).matches()) {
      names.add(name);
    }
  }
}

package dev.ward.analysis.domain.openapi.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the templates of a path: its variables, their names and the shape of the path. */
final class PathTemplates {

  private static final Pattern TEMPLATE = Pattern.compile("\\{[^{}]*\\}");

  private PathTemplates() {}

  static String normalized(final String path) {
    final Matcher matcher = TEMPLATE.matcher(path);
    return matcher.replaceAll(match -> "{}");
  }

  static List<String> names(final String path) {
    final List<String> names = new ArrayList<>();
    final Matcher matcher = TEMPLATE.matcher(path);
    while (matcher.find()) {
      final String name = templateName(matcher.group());
      if (!name.isEmpty()) {
        names.add(name);
      }
    }
    return names;
  }

  private static String templateName(final String template) {
    String inner = template.substring(1, template.length() - 1);
    if (!inner.isEmpty() && "+#./;?&".indexOf(inner.charAt(0)) >= 0) {
      inner = inner.substring(1);
    }
    inner = inner.endsWith("*") ? inner.substring(0, inner.length() - 1) : inner;
    final int colon = inner.lastIndexOf(':');
    return colon >= 0 && inner.substring(colon + 1).matches("[1-9][0-9]*")
        ? inner.substring(0, colon)
        : inner;
  }
}

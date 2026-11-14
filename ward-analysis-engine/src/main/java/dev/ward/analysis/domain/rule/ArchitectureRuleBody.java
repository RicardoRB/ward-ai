package dev.ward.analysis.domain.rule;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The text form of the rules of {@code ward-architecture.toml} in a rule body. One rule is a line
 * of tab-separated fields; every text is percent-encoded, so names, globs and reasons may hold any
 * character. A selector is three comma-separated lists joined by {@code ;}.
 */
public final class ArchitectureRuleBody {
  private static final String RULE_SEPARATOR = "\n";
  private static final String FIELD_SEPARATOR = "\t";
  private static final String LIST_SEPARATOR = ";";
  private static final String ITEM_SEPARATOR = ",";
  private static final String NONE = "";
  private static final int FIELDS = 6;
  private static final int LISTS = 3;

  private ArchitectureRuleBody() {}

  /** Returns the body text of {@code rules}, in order. */
  public static String encode(final List<ArchitectureRule> rules) {
    return rules.stream()
        .map(ArchitectureRuleBody::line)
        .collect(Collectors.joining(RULE_SEPARATOR));
  }

  /**
   * Reads the rules of a body written by {@link #encode(List)}.
   *
   * @throws IllegalArgumentException if the text is not a list of rules
   */
  public static List<ArchitectureRule> decode(final String body) {
    return body.isEmpty()
        ? List.of()
        : Arrays.stream(body.split(RULE_SEPARATOR, -1)).map(ArchitectureRuleBody::rule).toList();
  }

  private static String line(final ArchitectureRule rule) {
    final ArchitectureConstraint constraint = rule.constraint();
    final String value =
        constraint.target() == null ? text(constraint.fileName()) : selector(constraint.target());
    return String.join(
        FIELD_SEPARATOR,
        text(rule.name()),
        text(rule.because()),
        Boolean.toString(rule.allowEmpty()),
        constraint.kind().name(),
        selector(rule.select()),
        value);
  }

  private static ArchitectureRule rule(final String line) {
    final String[] fields = line.split(FIELD_SEPARATOR, -1);
    if (fields.length != FIELDS) {
      throw new IllegalArgumentException("an architecture rule has " + FIELDS + " fields");
    }
    final ArchitectureConstraint.Kind kind = ArchitectureConstraint.Kind.valueOf(fields[3]);
    final ArchitectureConstraint constraint =
        kind == ArchitectureConstraint.Kind.FILE_NAME
            ? new ArchitectureConstraint(kind, null, plain(fields[5]))
            : new ArchitectureConstraint(kind, selector(fields[5]), null);
    return new ArchitectureRule(
        plain(fields[0]),
        selector(fields[4]),
        constraint,
        plain(fields[1]),
        Boolean.parseBoolean(fields[2]));
  }

  private static String selector(final ArchitectureSelector selector) {
    return Stream.of(selector.paths(), selector.directories(), selector.excludePaths())
        .map(
            items ->
                items.isEmpty()
                    ? NONE
                    : items.stream()
                        .map(ArchitectureRuleBody::text)
                        .collect(Collectors.joining(ITEM_SEPARATOR)))
        .collect(Collectors.joining(LIST_SEPARATOR));
  }

  private static ArchitectureSelector selector(final String text) {
    final String[] lists = text.split(LIST_SEPARATOR, -1);
    if (lists.length != LISTS) {
      throw new IllegalArgumentException("a selector has " + LISTS + " lists");
    }
    return new ArchitectureSelector(items(lists[0]), items(lists[1]), items(lists[2]));
  }

  private static List<String> items(final String list) {
    return list.equals(NONE)
        ? List.of()
        : Arrays.stream(list.split(ITEM_SEPARATOR, -1)).map(ArchitectureRuleBody::plain).toList();
  }

  /** Percent-encodes {@code value}; an entry is never empty, so no list is written as NONE. */
  private static String text(final String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String plain(final String encoded) {
    return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
  }
}

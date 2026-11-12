package dev.ward.infrastructure.rule.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps the lines of a Juliet test case to the kind of method that holds them. A Juliet method named
 * {@code bad...} holds the vulnerable flow and one named {@code good...} holds the same flow made
 * safe, so a finding on a {@code bad} line is a true positive and one on a {@code good} line is a
 * false positive. Members of a case sit at four spaces of indentation.
 */
final class JulietMethods {

  /** The kind of method that holds a line. */
  enum Kind {
    /** A method of the vulnerable flow. */
    BAD,
    /** A method of the safe flow. */
    GOOD,
    /** Anything else, such as the entry point or a helper. */
    OTHER
  }

  private static final Pattern MEMBER =
      Pattern.compile(
          "^ {4}(?:public |private |protected )(?:static )?(?:final )?[\\w<>\\[\\].]+ (\\w+) ?\\(");
  private static final Pattern TYPE =
      Pattern.compile("^ {4}(?:public |private |static |final )*(?:class|interface|enum) ");
  private static final String BAD = "bad";
  private static final String GOOD = "good";

  private JulietMethods() {}

  /**
   * Returns the kind of every line of {@code source}, in order.
   *
   * @param source the text of a Juliet case
   * @return one kind per line
   */
  static List<Kind> kinds(final String source) {
    final List<Kind> kinds = new ArrayList<>();
    Kind current = Kind.OTHER;
    for (final String line : source.split("\n", -1)) {
      current = kindAfter(line, current);
      kinds.add(current);
    }
    return kinds;
  }

  private static Kind kindAfter(final String line, final Kind current) {
    final Matcher member = MEMBER.matcher(line);
    if (member.find()) {
      return kindOf(member.group(1));
    }
    return TYPE.matcher(line).find() ? Kind.OTHER : current;
  }

  private static Kind kindOf(final String name) {
    if (name.startsWith(BAD)) {
      return Kind.BAD;
    }
    return name.startsWith(GOOD) ? Kind.GOOD : Kind.OTHER;
  }

  /**
   * Tells whether the case has a method of the vulnerable flow.
   *
   * @param source the text of a Juliet case
   * @return true when some member is named {@code bad...}
   */
  static boolean hasBad(final String source) {
    return kinds(source).contains(Kind.BAD);
  }
}

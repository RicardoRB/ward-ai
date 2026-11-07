package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Reports a call whose first argument is a string literal that the callee cannot accept: a regular
 * expression that does not compile, a time zone ID the JDK does not know, or a zone ID that {@code
 * ZoneId.of} rejects.
 */
public final class LiteralArgumentCheck implements RuleCheck {

  /** What the literal must be. */
  public enum Mode {
    /** A regular expression. */
    REGEX,
    /** A {@code TimeZone} ID. */
    TIME_ZONE,
    /** A {@code ZoneId} ID. */
    ZONE_ID
  }

  private static final char BACKSLASH = (char) 92;
  private static final String EXPRESSION = "expression";
  private static final Pattern CUSTOM_ZONE = Pattern.compile("GMT[+-]\\d{1,2}(?::?\\d{2})?");
  private static final Set<String> TIME_ZONES =
      new HashSet<>(Arrays.asList(TimeZone.getAvailableIDs()));

  private final Mode mode;
  private final Pattern argument;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode what the literal must be
   * @param call a pattern for the start of the call text, such as {@code ^Pattern\.compile}
   * @param message the message; {@code {value}} is the literal
   */
  public LiteralArgumentCheck(final Mode mode, final String call, final String message) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.argument =
        Pattern.compile(
            "(?:" + Objects.requireNonNull(call, "call") + ")\\(\"((?:[^\"\\\\]|\\\\.)*)\"[,)]");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION))) {
      final Matcher found = argument.matcher(SyntaxWalk.text(visit.node()));
      if (found.find()) {
        final String literal = unescape(found.group(1));
        if (!valid(literal)) {
          matches.add(
              CheckSupport.at(visit.node().span(), CheckSupport.fill(message, literal, "")));
        }
      }
    }
    return List.copyOf(matches);
  }

  private boolean valid(final String literal) {
    return switch (mode) {
      case REGEX -> compiles(literal);
      case TIME_ZONE -> TIME_ZONES.contains(literal) || CUSTOM_ZONE.matcher(literal).matches();
      default -> zoneId(literal);
    };
  }

  private static boolean compiles(final String regex) {
    try {
      Pattern.compile(regex);
      return true;
    } catch (PatternSyntaxException e) {
      return false;
    }
  }

  private static boolean zoneId(final String id) {
    try {
      ZoneId.of(id);
      return true;
    } catch (DateTimeException e) {
      return false;
    }
  }

  private static String unescape(final String literal) {
    final StringBuilder out = new StringBuilder();
    boolean escape = false;
    for (final char next : literal.toCharArray()) {
      if (escape) {
        out.append(escaped(next));
        escape = false;
      } else if (next == BACKSLASH) {
        escape = true;
      } else {
        out.append(next);
      }
    }
    return out.toString();
  }

  private static char escaped(final char code) {
    return switch (code) {
      case 'n' -> '\n';
      case 't' -> '\t';
      default -> code;
    };
  }
}

package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a {@code "...".format(...)} call whose replacement field looks up an index or a key that
 * a literal argument does not have, like pylint {@code invalid-format-index}: {@code
 * "{0[5]}".format([1, 2])} or {@code "{0[key]}".format({"a": 1})}. Only literal arguments are
 * judged; a name or call argument gives no finding. Fields that look up a missing positional or
 * keyword argument are the argument-count rule's job and are not reported here.
 */
public final class FormatIndexCheck implements RuleCheck {

  private static final Pattern CALL =
      Pattern.compile(
          "^(\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*')\\.format\\((.*)\\)$", Pattern.DOTALL);
  private static final Pattern KEYWORD =
      Pattern.compile("^([A-Za-z_]\\w*)=(?!=)(.*)$", Pattern.DOTALL);
  private static final Set<String> CALLS = Set.of("atom_expr");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public FormatIndexCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CALLS)) {
      if (invalidLookup(SyntaxWalk.text(visit.node()))) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static boolean invalidLookup(final String text) {
    final Matcher call = CALL.matcher(text);
    if (!call.find()) {
      return false;
    }
    final String body = ArgumentList.unquote(call.group(1));
    final Arguments args = Arguments.of(ArgumentList.split(call.group(2)));
    if (args == null) {
      return false;
    }
    return fieldsFail(body, args);
  }

  private static boolean fieldsFail(final String body, final Arguments args) {
    int next = 0;
    int index = 0;
    while (index < body.length()) {
      final char c = body.charAt(index);
      if (c == '{' && index + 1 < body.length() && body.charAt(index + 1) == '{') {
        index += 2;
      } else if (c == '{') {
        final int end = body.indexOf('}', index);
        if (end < 0) {
          return false;
        }
        final String name = body.substring(index + 1, end).replaceAll("[:!].*$", "");
        final int open = name.indexOf('[');
        final String base = open < 0 ? name : name.substring(0, open);
        final String resolved = base.isEmpty() ? String.valueOf(next++) : base;
        if (open >= 0
            && name.indexOf(']', open) >= 0
            && lookupFails(resolved, name.substring(open + 1, name.indexOf(']', open)), args)) {
          return true;
        }
        index = end + 1;
      } else {
        index++;
      }
    }
    return false;
  }

  private static boolean lookupFails(
      final String base, final String accessor, final Arguments args) {
    final String value =
        base.chars().allMatch(Character::isDigit)
            ? args.positional(Integer.parseInt(base))
            : args.keyword(base);
    return value != null && accessorFails(value, accessor);
  }

  private static boolean accessorFails(final String value, final String accessor) {
    if (accessor.chars().allMatch(Character::isDigit) && !accessor.isEmpty()) {
      final List<String> items = sequence(value);
      return items != null && Integer.parseInt(accessor) >= items.size();
    }
    final Set<String> keys = dictKeys(value);
    return keys != null && !keys.contains(ArgumentList.unquote(accessor));
  }

  private static List<String> sequence(final String value) {
    if (!bracketed(value, '[', ']') && !(bracketed(value, '(', ')') && value.contains(","))) {
      return null;
    }
    return ArgumentList.split(value.substring(1, value.length() - 1));
  }

  private static Set<String> dictKeys(final String value) {
    if (!bracketed(value, '{', '}')) {
      return null;
    }
    final Set<String> keys = new HashSet<>();
    for (final String entry : ArgumentList.split(value.substring(1, value.length() - 1))) {
      final int colon = entry.indexOf(':');
      final String key = colon < 0 ? "" : entry.substring(0, colon).strip();
      if (!isQuoted(key)) {
        return null;
      }
      keys.add(ArgumentList.unquote(key));
    }
    return keys;
  }

  private static boolean isQuoted(final String text) {
    return text.length() >= 2 && (text.charAt(0) == '"' || text.charAt(0) == '\'');
  }

  private static boolean bracketed(final String value, final char open, final char close) {
    return value.length() >= 2
        && value.charAt(0) == open
        && value.charAt(value.length() - 1) == close;
  }

  /** The positional and keyword arguments of a call whose argument list is all literal-ish. */
  private record Arguments(List<String> positional, Map<String, String> keyword) {

    static Arguments of(final List<String> items) {
      final List<String> positional = new ArrayList<>();
      final Map<String, String> keyword = new HashMap<>();
      for (final String item : items) {
        if (item.startsWith("*")) {
          return null;
        }
        final Matcher named = KEYWORD.matcher(item);
        if (named.matches()) {
          keyword.put(named.group(1), named.group(2));
        } else {
          positional.add(item);
        }
      }
      return new Arguments(List.copyOf(positional), Map.copyOf(keyword));
    }

    String positional(final int index) {
      return index >= 0 && index < positional.size() ? positional.get(index) : null;
    }

    String keyword(final String name) {
      return keyword.get(name);
    }
  }
}

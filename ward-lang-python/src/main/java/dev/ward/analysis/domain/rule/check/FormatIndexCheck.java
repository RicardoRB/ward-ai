package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
  private static final char OPEN_BRACE = '{';
  private static final char CLOSE_BRACE = '}';
  private static final char OPEN_BRACKET = '[';
  private static final char CLOSE_BRACKET = ']';

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
    return Arguments.of(ArgumentList.split(call.group(2)))
        .map(args -> fieldsFail(body, args))
        .orElse(false);
  }

  private static boolean fieldsFail(final String body, final Arguments args) {
    final AtomicInteger next = new AtomicInteger();
    int index = 0;
    while (index < body.length()) {
      if (escapedBrace(body, index)) {
        index += 2;
      } else if (body.charAt(index) == OPEN_BRACE) {
        final int end = body.indexOf(CLOSE_BRACE, index);
        if (end < 0) {
          return false;
        }
        if (fieldFails(body.substring(index + 1, end), next, args)) {
          return true;
        }
        index = end + 1;
      } else {
        index++;
      }
    }
    return false;
  }

  private static boolean escapedBrace(final String body, final int index) {
    return body.charAt(index) == OPEN_BRACE
        && index + 1 < body.length()
        && body.charAt(index + 1) == OPEN_BRACE;
  }

  private static boolean fieldFails(
      final String field, final AtomicInteger next, final Arguments args) {
    final String name = field.replaceAll("[:!].*$", "");
    final int open = name.indexOf(OPEN_BRACKET);
    final String base = open < 0 ? name : name.substring(0, open);
    final String resolved = base.isEmpty() ? nextIndex(next) : base;
    final int close = open < 0 ? -1 : name.indexOf(CLOSE_BRACKET, open);
    return close >= 0 && lookupFails(resolved, name.substring(open + 1, close), args);
  }

  /** The automatic index of an empty replacement field; the counter advances once per field. */
  private static String nextIndex(final AtomicInteger next) {
    return String.valueOf(next.getAndIncrement());
  }

  private static boolean lookupFails(
      final String base, final String accessor, final Arguments args) {
    final Optional<String> value =
        base.chars().allMatch(Character::isDigit)
            ? args.positional(Integer.parseInt(base))
            : args.keyword(base);
    return value.map(literal -> accessorFails(literal, accessor)).orElse(false);
  }

  private static boolean accessorFails(final String value, final String accessor) {
    if (accessor.chars().allMatch(Character::isDigit) && !accessor.isEmpty()) {
      return LiteralSequences.sequence(value)
          .map(items -> Integer.parseInt(accessor) >= items.size())
          .orElse(false);
    }
    return LiteralSequences.dictKeys(value)
        .map(keys -> !keys.contains(ArgumentList.unquote(accessor)))
        .orElse(false);
  }

  /** The positional and keyword arguments of a call whose argument list is all literal-ish. */
  private record Arguments(List<String> positional, Map<String, String> keyword) {

    /** The arguments, or empty when a star argument makes the positions unknown. */
    static Optional<Arguments> of(final List<String> items) {
      final List<String> positional = new ArrayList<>();
      final Map<String, String> keyword = new HashMap<>();
      for (final String item : items) {
        if (item.startsWith("*")) {
          return Optional.empty();
        }
        final Matcher named = KEYWORD.matcher(item);
        if (named.matches()) {
          keyword.put(named.group(1), named.group(2));
        } else {
          positional.add(item);
        }
      }
      return Optional.of(new Arguments(List.copyOf(positional), Map.copyOf(keyword)));
    }

    Optional<String> positional(final int index) {
      return index >= 0 && index < positional.size()
          ? Optional.of(positional.get(index))
          : Optional.empty();
    }

    Optional<String> keyword(final String name) {
      return Optional.ofNullable(keyword.get(name));
    }
  }
}

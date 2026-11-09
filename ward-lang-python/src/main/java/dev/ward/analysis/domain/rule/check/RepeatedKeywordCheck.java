package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a call that passes the same keyword twice: once as {@code name=value} and once as a key
 * of a literal dictionary unpacked with {@code **{...}}. Only literal string keys are compared; a
 * dictionary that is not a display is not read.
 */
public final class RepeatedKeywordCheck implements RuleCheck {

  private static final String DOUBLE_STAR = "**";
  private static final String ARGLIST = "arglist";
  private static final int KEYWORD_PARTS = 3;
  private static final int DISPLAY_PARTS = 3;
  private static final String EQUALS = "=";
  private static final String COLON = ":";
  private static final String COMMA = ",";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public RepeatedKeywordCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("atom_expr"))) {
      final SyntaxNode call = visit.node();
      if (repeats(call)) {
        found.add(new RuleMatch(call.span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static boolean repeats(final SyntaxNode call) {
    final List<SyntaxNode> parts = call.children();
    if (parts.size() != 2 || !"trailer".equals(parts.get(1).kind())) {
      return false;
    }
    final List<String> keywords = new ArrayList<>();
    final List<String> keys = new ArrayList<>();
    for (final SyntaxNode list : parts.get(1).children()) {
      if (ARGLIST.equals(list.kind())) {
        for (final SyntaxNode argument : list.children()) {
          collect(argument, keywords, keys);
        }
      }
    }
    return keywords.stream().anyMatch(keys::contains);
  }

  private static void collect(
      final SyntaxNode argument, final List<String> keywords, final List<String> keys) {
    final List<SyntaxNode> parts = argument.children();
    if (parts.size() == KEYWORD_PARTS && EQUALS.equals(SyntaxWalk.text(parts.get(1)))) {
      keywords.add(SyntaxWalk.text(parts.get(0)));
    } else if (parts.size() == 2 && DOUBLE_STAR.equals(SyntaxWalk.text(parts.get(0)))) {
      keys.addAll(dictionaryKeys(parts.get(1)));
    }
  }

  /** The string keys of the dictionary display that an expression starts with; empty otherwise. */
  private static List<String> dictionaryKeys(final SyntaxNode expression) {
    final List<String> keys = new ArrayList<>();
    SyntaxWalk.visits(expression, Set.of("atom")).stream()
        .findFirst()
        .ifPresent(
            first -> {
              final List<SyntaxNode> parts = first.node().children();
              if (parts.size() == DISPLAY_PARTS
                  && "{".equals(SyntaxWalk.text(parts.get(0)))
                  && "dictorsetmaker".equals(parts.get(1).kind())) {
                readKeys(parts.get(1).children(), keys);
              }
            });
    return keys;
  }

  private static void readKeys(final List<SyntaxNode> items, final List<String> keys) {
    for (int index = 0; index + 1 < items.size(); index++) {
      if (COLON.equals(SyntaxWalk.text(items.get(index + 1)))
          && !COMMA.equals(SyntaxWalk.text(items.get(index)))) {
        final String key = SyntaxWalk.text(items.get(index));
        if (key.length() >= 2
            && (key.startsWith("\"") && key.endsWith("\"")
                || key.startsWith("'") && key.endsWith("'"))) {
          keys.add(key.substring(1, key.length() - 1));
        }
      }
    }
  }
}

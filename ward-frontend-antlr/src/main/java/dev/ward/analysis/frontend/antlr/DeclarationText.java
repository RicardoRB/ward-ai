package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Logger;
import org.antlr.v4.runtime.Token;

/**
 * Blanks the bodies of functions, constructors, initializers and accessors of a Java or Kotlin
 * file, so that a parse reads declarations only. The lexer finds the blocks; a body becomes spaces,
 * with its line breaks kept, so every line and column of what remains is unchanged. A block whose
 * role is not certain stays as written, and a text that does not lex or whose braces do not match
 * is returned as it is, so the parser reports its errors as before.
 */
public final class DeclarationText {
  private static final Logger LOG = Logger.getLogger(DeclarationText.class.getName());

  private DeclarationText() {}

  /**
   * Returns the unit with its bodies blanked; units of other languages come back unchanged.
   *
   * @param unit the source unit
   * @return a unit with the same path and language, and the text of its declarations
   */
  public static SourceUnit of(final SourceUnit unit) {
    final MemberRules rules = rules(unit.language());
    if (rules == null) {
      return unit;
    }
    final List<Token> tokens = rules.lex(unit.path(), unit.text());
    final List<int[]> ranges = tokens.isEmpty() ? List.of() : bodies(tokens, rules);
    if (ranges.isEmpty()) {
      return unit;
    }
    LOG.fine(() -> "bodies blanked: " + ranges.size());
    return new SourceUnit(unit.path(), unit.language(), blank(unit.text(), ranges));
  }

  private static MemberRules rules(final AnalysisLanguage language) {
    return switch (language) {
      case JAVA -> new JavaMembers();
      case KOTLIN -> new KotlinMembers();
      default -> null;
    };
  }

  /** The offsets {start, end} of each body, or none if the blocks do not match. */
  private static List<int[]> bodies(final List<Token> tokens, final MemberRules rules) {
    final BodyWalk walk = new BodyWalk(rules);
    for (final Token token : tokens) {
      if (!walk.step(token)) {
        return List.of();
      }
    }
    return walk.finished() ? walk.ranges : List.of();
  }

  private static String blank(final String text, final List<int[]> ranges) {
    final StringBuilder out = new StringBuilder(text);
    for (final int[] range : ranges) {
      for (int at = range[0]; at < range[1]; at++) {
        final char c = out.charAt(at);
        if (c != '\n' && c != '\r') {
          out.setCharAt(at, ' ');
        }
      }
    }
    return out.toString();
  }

  /** The state of one pass over the tokens of a file. */
  private static final class BodyWalk {
    private final MemberRules rules;
    private final List<int[]> ranges = new ArrayList<>();
    /** One entry per open type body: whether it is still in its enum constants. */
    private final Deque<boolean[]> types = new ArrayDeque<>();
    private Token previous;
    private MemberRules.Kind previousKind = MemberRules.Kind.NEWLINE;
    private int parens;
    private int skipping;
    private int skipFrom = -1;

    BodyWalk(final MemberRules rules) {
      this.rules = rules;
    }

    boolean finished() {
      return skipping == 0 && types.isEmpty();
    }

    /** Takes one token; returns false when the blocks cannot be matched. */
    boolean step(final Token token) {
      final MemberRules.Kind kind = rules.kind(token);
      final boolean ok = skipping > 0 ? skip(token, kind) : walk(token, kind);
      previous = token;
      previousKind = kind;
      return ok;
    }

    private boolean skip(final Token token, final MemberRules.Kind kind) {
      if (kind == MemberRules.Kind.OPEN || kind == MemberRules.Kind.TEMPLATE_OPEN) {
        skipping++;
      } else if (kind == MemberRules.Kind.CLOSE) {
        skipping--;
        if (skipping == 0 && skipFrom >= 0) {
          ranges.add(new int[] {skipFrom, token.getStartIndex()});
          skipFrom = -1;
          rules.reset();
        }
      }
      return true;
    }

    private boolean walk(final Token token, final MemberRules.Kind kind) {
      switch (kind) {
        case LEFT_PAREN -> parens++;
        case RIGHT_PAREN -> parens = Math.max(0, parens - 1);
        case OPEN -> open(token);
        case TEMPLATE_OPEN -> skipping = 1;
        case CLOSE -> {
          return close();
        }
        case END -> end();
        case NEWLINE -> {
          // only marks the first token of the next line
        }
        default -> note(token);
      }
      return true;
    }

    private void note(final Token token) {
      if (parens == 0) {
        rules.see(token, previous, previousKind == MemberRules.Kind.NEWLINE);
      }
    }

    private void end() {
      if (parens == 0) {
        if (!types.isEmpty()) {
          types.peek() [0] = false;
        }
        rules.reset();
      }
    }

    private boolean close() {
      if (types.isEmpty()) {
        return false;
      }
      types.pop();
      rules.reset();
      return true;
    }

    private void open(final Token token) {
      final MemberRules.Action action =
          parens > 0
              ? MemberRules.Action.KEEP
              : rules.open(previous, !types.isEmpty(), !types.isEmpty() && types.peek() [0]);
      if (action == MemberRules.Action.TYPE) {
        types.push(new boolean[] {rules.enumHead()});
        rules.reset();
      } else {
        skipping = 1;
        skipFrom = action == MemberRules.Action.STRIP ? token.getStopIndex() + 1 : -1;
      }
    }
  }
}

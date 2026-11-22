package dev.ward.analysis.frontend.antlr.format.rust;

import dev.ward.analysis.frontend.antlr.rust.RustParser;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * What the tokens alone cannot tell, read from the parse tree: which {@code <} and {@code >} are
 * generic brackets, which {@code |} delimit closure parameters or separate pattern alternatives,
 * the braces of a {@code use} group and the tokens inside a macro's brackets.
 */
final class RustRoles {
  /** The part a token plays in the syntax, when the tokens do not show it. */
  enum Role {
    NONE,
    OPEN_ATTACHED,
    OPEN_QUALIFIED,
    CLOSE_ANGLE,
    CLOSURE_OPEN,
    CLOSURE_CLOSE,
    USE_BRACE,
    PATTERN_BAR,
    MACRO
  }

  private static final Set<Integer> OPENING =
      Set.of(RustParser.LPAREN, RustParser.LSQUAREBRACKET, RustParser.LCURLYBRACE);
  private static final Set<Integer> CLOSING =
      Set.of(RustParser.RPAREN, RustParser.RSQUAREBRACKET, RustParser.RCURLYBRACE);

  private final List<Role> roles;

  private RustRoles(final List<Role> roles) {
    this.roles = List.copyOf(roles);
  }

  /**
   * Reads the roles of every token.
   *
   * @param tokens the significant tokens of the source
   * @param root the root of their parse tree
   * @return the roles, by token position
   */
  static RustRoles of(final List<Token> tokens, final ParserRuleContext root) {
    final Marks marks = new Marks(tokens);
    final Deque<ParseTree> pending = new ArrayDeque<>();
    pending.push(root);
    while (!pending.isEmpty()) {
      final ParseTree node = pending.pop();
      if (node instanceof ParserRuleContext context) {
        mark(context, marks);
      }
      for (int i = 0; i < node.getChildCount(); i++) {
        pending.push(node.getChild(i));
      }
    }
    return new RustRoles(marks.roles());
  }

  /**
   * The role of one token.
   *
   * @param position the token's position
   * @return its role, {@code NONE} for most tokens
   */
  Role at(final int position) {
    return roles.get(position);
  }

  private static void mark(final ParserRuleContext context, final Marks marks) {
    if (context instanceof RustParser.GenericParamsContext
        || context instanceof RustParser.GenericArgsContext
        || context instanceof RustParser.UseBoundContext) {
      angles(context, Role.OPEN_ATTACHED, marks);
    } else if (context instanceof RustParser.QualifiedPathTypeContext) {
      angles(context, Role.OPEN_QUALIFIED, marks);
    } else if (context instanceof RustParser.ClosureExpressionContext) {
      pipes(context, marks);
    } else {
      markDelimited(context, marks);
    }
  }

  private static void markDelimited(final ParserRuleContext context, final Marks marks) {
    if (context instanceof RustParser.UseTreeContext) {
      terminals(context, RustParser.LCURLYBRACE, Role.USE_BRACE, marks);
      terminals(context, RustParser.RCURLYBRACE, Role.USE_BRACE, marks);
    } else if (context instanceof RustParser.PatternContext) {
      terminals(context, RustParser.OR, Role.PATTERN_BAR, marks);
    } else if (context instanceof RustParser.MacroInvocationSemiContext) {
      delimiters(context, marks);
    } else if (context instanceof RustParser.DelimTokenTreeContext
        || context instanceof RustParser.MacroRulesDefContext) {
      marks.range(
          marks.positionOf(context.getStart()) + 1,
          marks.positionOf(context.getStop()),
          Role.MACRO
      );
    }
  }

  private static void angles(final ParserRuleContext context, final Role open, final Marks marks) {
    terminals(context, RustParser.LT, open, marks);
    terminals(context, RustParser.GT, Role.CLOSE_ANGLE, marks);
  }

  private static void pipes(final ParserRuleContext context, final Marks marks) {
    boolean first = true;
    for (final ParseTree child : children(context)) {
      if (child instanceof TerminalNode node && node.getSymbol().getType() == RustParser.OR) {
        marks.set(node.getSymbol(), first ? Role.CLOSURE_OPEN : Role.CLOSURE_CLOSE);
        first = false;
      }
    }
  }

  private static void terminals(
      final ParserRuleContext context,
      final int type,
      final Role role,
      final Marks marks
  ) {
    for (final ParseTree child : children(context)) {
      if (child instanceof TerminalNode node && node.getSymbol().getType() == type) {
        marks.set(node.getSymbol(), role);
      }
    }
  }

  /** The brackets of a macro statement are direct children of its rule, so the span is found. */
  private static void delimiters(final ParserRuleContext context, final Marks marks) {
    int first = -1;
    int last = -1;
    for (final ParseTree child : children(context)) {
      if (child instanceof TerminalNode node) {
        final int type = node.getSymbol().getType();
        final int at = marks.positionOf(node.getSymbol());
        if (first < 0 && OPENING.contains(type)) {
          first = at;
        } else if (CLOSING.contains(type)) {
          last = at;
        }
      }
    }
    marks.range(first + 1, last, Role.MACRO);
  }

  private static List<ParseTree> children(final ParserRuleContext context) {
    return context.children == null ? List.of() : List.copyOf(context.children);
  }

  /** The roles being filled, with the position of every token by its stream index. */
  private static final class Marks {
    private final Map<Integer, Integer> positions = new HashMap<>();
    private final Role[] roles;

    Marks(final List<Token> tokens) {
      this.roles = new Role[tokens.size()];
      Arrays.fill(roles, Role.NONE);
      for (int i = 0; i < tokens.size(); i++) {
        positions.put(tokens.get(i).getTokenIndex(), i);
      }
    }

    int positionOf(final Token token) {
      return positions.get(token.getTokenIndex());
    }

    void set(final Token token, final Role role) {
      roles[positionOf(token)] = role;
    }

    void range(final int from, final int to, final Role role) {
      for (int i = from; i < to; i++) {
        roles[i] = role;
      }
    }

    List<Role> roles() {
      return Arrays.asList(roles);
    }
  }
}

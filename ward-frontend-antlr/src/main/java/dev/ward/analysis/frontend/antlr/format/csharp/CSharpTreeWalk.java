package dev.ward.analysis.frontend.antlr.format.csharp;

import dev.ward.analysis.domain.format.FormatRule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Walks a C# parse tree once, in pre-order, to collect what the formatter needs: the rule kinds,
 * the role of the tokens whose space depends on their place in the tree, the extra indent levels
 * of embedded statements and switch sections, and the tokens of interpolated strings.
 */
final class CSharpTreeWalk {
  private static final String EMBEDDED_STATEMENT = "embedded_statement";
  private static final String STATEMENT_LIST = "statement_list";
  private static final String SWITCH_SECTION = "switch_section";
  private static final String INTERPOLATED_STRING = "interpolated_string_expression";

  private final List<FormatRule> rules = new ArrayList<>();
  private final Map<Integer, CSharpRole> roles = new HashMap<>();
  private final Map<Integer, Integer> strings = new HashMap<>();
  private final int[] extra;
  private int stringCount;
  private boolean inString;

  /**
   * Walks the tree below a root.
   *
   * @param root the root of the parse tree
   * @param tokenCount the number of tokens in the token stream
   */
  CSharpTreeWalk(final ParserRuleContext root, final int tokenCount) {
    this.extra = new int[tokenCount + 1];
    visit(root);
    for (int i = 1; i < extra.length; i++) {
      extra[i] += extra[i - 1];
    }
  }

  /**
   * Returns the rule kinds in pre-order.
   *
   * @return the rules
   */
  List<FormatRule> rules() {
    return List.copyOf(rules);
  }

  /**
   * Returns the role of a token.
   *
   * @param tokenIndex the token's index in the token stream
   * @return the role, {@link CSharpRole#NONE} when the tree gives none
   */
  CSharpRole role(final int tokenIndex) {
    return roles.getOrDefault(tokenIndex, CSharpRole.NONE);
  }

  /**
   * Returns the interpolated string expression that a token belongs to.
   *
   * @param tokenIndex the token's index in the token stream
   * @return the expression's number, or -1 when the token is not part of one
   */
  int stringOf(final int tokenIndex) {
    return strings.getOrDefault(tokenIndex, -1);
  }

  /**
   * Returns the extra indent levels that the tree adds to a token.
   *
   * @param tokenIndex the token's index in the token stream
   * @return the levels
   */
  int extraLevels(final int tokenIndex) {
    return extra[tokenIndex];
  }

  private void visit(final ParserRuleContext context) {
    final String rule = CSharpRoles.ruleOf(context);
    rules.add(new FormatRule(rule, context.getStart().getLine()));
    if (context.children == null) {
      return;
    }
    markExtra(context, rule);
    final boolean enteredString = INTERPOLATED_STRING.equals(rule) && !inString;
    if (enteredString) {
      inString = true;
      stringCount++;
    }
    for (int i = 0; i < context.children.size(); i++) {
      final ParseTree child = context.children.get(i);
      if (child instanceof ParserRuleContext nested) {
        visit(nested);
      } else if (child instanceof TerminalNode terminal) {
        visitTerminal(context, i, terminal.getSymbol());
      }
    }
    if (enteredString) {
      inString = false;
    }
  }

  private void visitTerminal(
      final ParserRuleContext context,
      final int position,
      final Token token
  ) {
    if (inString) {
      strings.put(token.getTokenIndex(), stringCount);
      return;
    }
    final CSharpRole role = CSharpRoles.of(context, position, token.getText());
    if (role != CSharpRole.NONE) {
      roles.put(token.getTokenIndex(), role);
    }
  }

  /** Adds one level to the tokens of a statement body that is not a block, and to a section. */
  private void markExtra(final ParserRuleContext context, final String rule) {
    final boolean body = EMBEDDED_STATEMENT.equals(rule) && isIndentedBody(context);
    final boolean section =
        STATEMENT_LIST.equals(rule) && SWITCH_SECTION.equals(
            CSharpRoles.ruleOf(context.getParent())
        );
    if ((body || section) && context.getStop() != null) {
      extra[context.getStart().getTokenIndex()]++;
      extra[context.getStop().getTokenIndex() + 1]--;
    }
  }

  /** A body that follows its statement keyword, but not a block and not an {@code else if}. */
  private static boolean isIndentedBody(final ParserRuleContext context) {
    final ParserRuleContext parent = context.getParent();
    if ("statement".equals(CSharpRoles.ruleOf(parent))
        || "block".equals(CSharpRoles.ruleOf(context.getChild(0)))) {
      return false;
    }
    final int index = parent.children.indexOf(context);
    final boolean afterElse = index > 0 && "else".equals(parent.getChild(index - 1).getText());
    return !(afterElse && "selection_statement".equals(CSharpRoles.ruleOf(context.getChild(0))));
  }
}

package dev.ward.analysis.frontend.antlr.format.kotlin;

import dev.ward.analysis.domain.format.FormatRule;
import dev.ward.analysis.frontend.antlr.kotlin.KotlinLexer;
import dev.ward.analysis.frontend.antlr.kotlin.KotlinParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Walks a Kotlin parse tree once, in pre-order, to collect what the formatter needs: the rule
 * kinds, the parentheses that open argument lists, and the expressions that follow an assignment
 * sign.
 */
final class KotlinTreeWalk {

  /** The rule that only matches newlines and semicolons, whose count a blank line changes. */
  private static final String WHITESPACE_RULE = "anysemi";

  private final List<FormatRule> rules = new ArrayList<>();
  private final Set<Integer> argumentLists = new HashSet<>();
  private final Map<Integer, Integer> continuations = new HashMap<>();
  private final Set<Integer> superColons = new HashSet<>();

  /**
   * Walks the tree below a root.
   *
   * @param root the root of the parse tree
   */
  KotlinTreeWalk(final ParserRuleContext root) {
    visit(root);
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
   * Returns the token indexes of the parentheses that open argument or parameter lists.
   *
   * @return the indexes
   */
  Set<Integer> argumentLists() {
    return Set.copyOf(argumentLists);
  }

  /**
   * Returns the colons that introduce the supertypes of a class or object, which take a space
   * before them.
   *
   * @return the token indexes
   */
  Set<Integer> superColons() {
    return Set.copyOf(superColons);
  }

  /**
   * Returns the expressions that follow an assignment sign: each sign's token index maps to the
   * token index of the expression's last token.
   *
   * @return the map
   */
  Map<Integer, Integer> continuations() {
    return Map.copyOf(continuations);
  }

  private void visit(final ParserRuleContext context) {
    final String rule = KotlinParser.ruleNames[context.getRuleIndex()];
    if (!WHITESPACE_RULE.equals(rule)) {
      rules.add(new FormatRule(rule, context.getStart().getLine()));
    }
    if (isArgumentList(context)) {
      argumentLists.add(context.getStart().getTokenIndex());
    }
    if (context.children == null) {
      return;
    }
    if (context instanceof KotlinParser.ClassDeclarationContext
        || context instanceof KotlinParser.ObjectDeclarationContext) {
      addSuperColons(context);
    }
    for (int i = 0; i < context.children.size(); i++) {
      final ParseTree child = context.children.get(i);
      if (child instanceof ParserRuleContext nested) {
        visit(nested);
      }
      addContinuation(context, i);
    }
  }

  /** Maps an assignment sign, and the expression after any newlines, to the expression's end. */
  private void addContinuation(final ParserRuleContext context, final int index) {
    if (!(context.children.get(index) instanceof TerminalNode sign)
        || sign.getSymbol().getType() != KotlinLexer.ASSIGNMENT) {
      return;
    }
    int next = index + 1;
    while (next < context.children.size() && isNewline(context.children.get(next))) {
      next++;
    }
    if (next < context.children.size()
        && context.children.get(next) instanceof ParserRuleContext expression
        && expression.getStop() != null) {
      continuations.put(sign.getSymbol().getTokenIndex(), expression.getStop().getTokenIndex());
    }
  }

  private void addSuperColons(final ParserRuleContext context) {
    for (final ParseTree child : context.children) {
      if (child instanceof TerminalNode colon && colon.getSymbol().getType() == KotlinLexer.COLON) {
        superColons.add(colon.getSymbol().getTokenIndex());
      }
    }
  }

  private static boolean isNewline(final ParseTree node) {
    return node instanceof TerminalNode terminal
        && terminal.getSymbol().getType() == KotlinLexer.NL;
  }

  private static boolean isArgumentList(final ParserRuleContext context) {
    return context instanceof KotlinParser.ValueArgumentsContext
        || context instanceof KotlinParser.FunctionValueParametersContext
        || context instanceof KotlinParser.ClassParametersContext;
  }
}

package dev.ward.analysis.frontend.antlr.javascript;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.atn.AtomTransition;
import org.antlr.v4.runtime.atn.DecisionState;
import org.antlr.v4.runtime.atn.PrecedencePredicateTransition;
import org.antlr.v4.runtime.atn.RuleTransition;
import org.antlr.v4.runtime.atn.StarLoopEntryState;
import org.antlr.v4.runtime.atn.Transition;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * The decisions that {@link QuickPrediction} answers, and their alternatives, read from the ATN of
 * the JavaScript parser, so a change to the grammar cannot shift them.
 */
final class QuickDecisions {
  /** The statement rule of each reserved keyword that starts one. */
  private static final Map<Integer, Integer> STATEMENT_RULES =
      Map.ofEntries(
          Map.entry(JavaScriptParser.Try, JavaScriptParser.RULE_tryStatement),
          Map.entry(JavaScriptParser.If, JavaScriptParser.RULE_ifStatement),
          Map.entry(JavaScriptParser.While, JavaScriptParser.RULE_iterationStatement),
          Map.entry(JavaScriptParser.Do, JavaScriptParser.RULE_iterationStatement),
          Map.entry(JavaScriptParser.For, JavaScriptParser.RULE_iterationStatement),
          Map.entry(JavaScriptParser.Switch, JavaScriptParser.RULE_switchStatement),
          Map.entry(JavaScriptParser.Return, JavaScriptParser.RULE_returnStatement),
          Map.entry(JavaScriptParser.Throw, JavaScriptParser.RULE_throwStatement),
          Map.entry(JavaScriptParser.Break, JavaScriptParser.RULE_breakStatement),
          Map.entry(JavaScriptParser.Continue, JavaScriptParser.RULE_continueStatement),
          Map.entry(JavaScriptParser.Debugger, JavaScriptParser.RULE_debuggerStatement),
          Map.entry(JavaScriptParser.With, JavaScriptParser.RULE_withStatement),
          Map.entry(JavaScriptParser.Var, JavaScriptParser.RULE_variableStatement),
          Map.entry(JavaScriptParser.Const, JavaScriptParser.RULE_variableStatement),
          Map.entry(JavaScriptParser.Class, JavaScriptParser.RULE_classDeclaration)
      );

  private static final List<Integer> PREFIX_OPERATORS =
      List.of(
          JavaScriptParser.Await,
          JavaScriptParser.Typeof,
          JavaScriptParser.Void,
          JavaScriptParser.Delete
      );

  /** The forms of {@code new}: a name and arguments, an operand and arguments, an operand. */
  private static final int NEW_FORMS = 3;

  final DecisionState statement;
  final Map<Integer, Integer> statementAlternatives;
  final DecisionState primary;
  final Map<Integer, Integer> operatorAlternatives;
  final List<Integer> newAlternatives;
  final int functionAlternative;
  final StarLoopEntryState loop;
  final List<Suffix> suffixes;
  final DecisionState ifElse;

  /** One operator alternative of the expression loop: its precedence and the tokens it starts. */
  record Suffix(int precedence, IntervalSet first) {}

  /**
   * Reads the decisions.
   *
   * @param atn the ATN of the JavaScript parser
   */
  QuickDecisions(final ATN atn) {
    statement = first(atn, JavaScriptParser.RULE_statement);
    statementAlternatives = byRule(statement);
    primary = first(atn, JavaScriptParser.RULE_singleExpression);
    operatorAlternatives = byToken(primary);
    newAlternatives = alternativesOf(primary, JavaScriptParser.New);
    functionAlternative =
        AtnPaths.alternativeTo(primary, JavaScriptParser.RULE_anonymousFunction);
    loop = AtnPaths.expressionLoop(atn);
    suffixes = suffixes(atn, loop);
    ifElse = first(atn, JavaScriptParser.RULE_ifStatement);
  }

  /** Returns the statement alternative of a reserved keyword, or {@link AtnPaths#NONE}. */
  int statementOf(final int keyword) {
    final Integer rule = STATEMENT_RULES.get(keyword);
    return rule == null ? AtnPaths.NONE : statementAlternatives.getOrDefault(rule, AtnPaths.NONE);
  }

  /** Returns the alternative of a prefix operator, or {@link AtnPaths#NONE}. */
  int operatorOf(final int token) {
    return PREFIX_OPERATORS.contains(token)
        ? operatorAlternatives.getOrDefault(token, AtnPaths.NONE)
        : AtnPaths.NONE;
  }

  /** Tells whether every decision and alternative that is answered was found in the ATN. */
  boolean complete() {
    return statementAlternatives.keySet().containsAll(STATEMENT_RULES.values())
        && operatorAlternatives.keySet().containsAll(PREFIX_OPERATORS)
        && newAlternatives.size() >= NEW_FORMS
        && !suffixes.isEmpty()
        && functionAlternative != AtnPaths.NONE;
  }

  private static DecisionState first(final ATN atn, final int rule) {
    return AtnPaths.firstDecision(atn, atn.ruleToStartState[rule]);
  }

  private static List<Integer> alternativesOf(final DecisionState decision, final int token) {
    final List<Integer> alternatives = new ArrayList<>();
    for (int alt = 0; alt < decision.getNumberOfTransitions(); alt++) {
      if (AtnPaths.firstTransition(
          decision.transition(alt).target
      ) instanceof AtomTransition atom && atom.label == token) {
        alternatives.add(alt + 1);
      }
    }
    return alternatives;
  }

  private static Map<Integer, Integer> byRule(final DecisionState decision) {
    final Map<Integer, Integer> alternatives = new HashMap<>();
    for (int alt = 0; alt < decision.getNumberOfTransitions(); alt++) {
      if (AtnPaths.firstTransition(
          decision.transition(alt).target
      ) instanceof RuleTransition rule) {
        alternatives.putIfAbsent(rule.ruleIndex, alt + 1);
      }
    }
    return alternatives;
  }

  private static Map<Integer, Integer> byToken(final DecisionState decision) {
    final Map<Integer, Integer> alternatives = new HashMap<>();
    for (int alt = 0; alt < decision.getNumberOfTransitions(); alt++) {
      if (AtnPaths.firstTransition(
          decision.transition(alt).target
      ) instanceof AtomTransition atom) {
        alternatives.putIfAbsent(atom.label, alt + 1);
      }
    }
    return alternatives;
  }

  private static List<Suffix> suffixes(final ATN atn, final StarLoopEntryState loop) {
    final List<Suffix> suffixes = new ArrayList<>();
    final ATNState block = loop.transition(0).target;
    for (int alt = 0; alt < block.getNumberOfTransitions(); alt++) {
      final Transition first = AtnPaths.firstTransition(block.transition(alt).target);
      if (first instanceof PrecedencePredicateTransition predicate) {
        suffixes.add(new Suffix(predicate.precedence, atn.nextTokens(predicate.target)));
      }
    }
    return suffixes;
  }
}

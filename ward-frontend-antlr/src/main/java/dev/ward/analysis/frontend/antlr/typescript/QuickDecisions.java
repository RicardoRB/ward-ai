package dev.ward.analysis.frontend.antlr.typescript;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
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
 * the TypeScript parser, so a change to the grammar cannot shift them.
 */
final class QuickDecisions {
  /** The statement rule of each reserved keyword that starts one. */
  private static final Map<Integer, Integer> STATEMENT_RULES =
      Map.ofEntries(
          Map.entry(TypeScriptParser.Try, TypeScriptParser.RULE_tryStatement),
          Map.entry(TypeScriptParser.If, TypeScriptParser.RULE_ifStatement),
          Map.entry(TypeScriptParser.While, TypeScriptParser.RULE_iterationStatement),
          Map.entry(TypeScriptParser.Do, TypeScriptParser.RULE_iterationStatement),
          Map.entry(TypeScriptParser.For, TypeScriptParser.RULE_iterationStatement),
          Map.entry(TypeScriptParser.Switch, TypeScriptParser.RULE_switchStatement),
          Map.entry(TypeScriptParser.Return, TypeScriptParser.RULE_returnStatement),
          Map.entry(TypeScriptParser.Throw, TypeScriptParser.RULE_throwStatement),
          Map.entry(TypeScriptParser.Break, TypeScriptParser.RULE_breakStatement),
          Map.entry(TypeScriptParser.Continue, TypeScriptParser.RULE_continueStatement),
          Map.entry(TypeScriptParser.Debugger, TypeScriptParser.RULE_debuggerStatement),
          Map.entry(TypeScriptParser.With, TypeScriptParser.RULE_withStatement),
          Map.entry(TypeScriptParser.Var, TypeScriptParser.RULE_variableStatement),
          Map.entry(TypeScriptParser.Const, TypeScriptParser.RULE_variableStatement),
          Map.entry(TypeScriptParser.Let, TypeScriptParser.RULE_variableStatement),
          Map.entry(TypeScriptParser.Class, TypeScriptParser.RULE_classDeclaration)
      );

  /** The rules where a brace after the signature opens a body. */
  private static final Set<Integer> BODY_RULES =
      Set.of(
          TypeScriptParser.RULE_propertyMemberDeclaration,
          TypeScriptParser.RULE_constructorDeclaration
      );

  private static final Set<Integer> PREFIX_OPERATORS =
      Set.of(
          TypeScriptParser.Await,
          TypeScriptParser.Typeof,
          TypeScriptParser.Void,
          TypeScriptParser.Delete
      );

  /** The forms of {@code new}: a name and arguments, an operand and arguments, an operand. */
  private static final int NEW_FORMS = 3;

  final DecisionState statement;
  final Map<Integer, Integer> statementAlternatives;
  final DecisionState primary;
  final Map<Integer, Integer> operatorAlternatives;
  final int functionAlternative;
  final StarLoopEntryState loop;
  final List<Suffix> suffixes;
  final DecisionState typeArguments;
  final DecisionState exportPrefix;
  final DecisionState classElement;
  final int memberAlternative;
  final DecisionState memberKind;
  final int accessorAlternative;
  final DecisionState ifElse;
  final DecisionState type;
  final int plainTypeAlternative;
  final List<Integer> newAlternatives;
  final int methodAlternative;
  final Set<Integer> bodies;

  /** One operator alternative of the expression loop: its precedence and the tokens it starts. */
  record Suffix(int precedence, IntervalSet first) {}

  /**
   * Reads the decisions.
   *
   * @param atn the ATN of the TypeScript parser
   */
  QuickDecisions(final ATN atn) {
    statement = first(atn, TypeScriptParser.RULE_statement);
    statementAlternatives = byRule(statement);
    primary = first(atn, TypeScriptParser.RULE_singleExpression);
    operatorAlternatives = byToken(primary);
    functionAlternative =
        AtnPaths.alternativeTo(primary, TypeScriptParser.RULE_anonymousFunction, -1);
    loop = AtnPaths.expressionLoop(atn);
    suffixes = suffixes(atn, loop);
    typeArguments = first(atn, TypeScriptParser.RULE_typeReference);
    exportPrefix = first(atn, TypeScriptParser.RULE_sourceElement);
    classElement = first(atn, TypeScriptParser.RULE_classElement);
    memberAlternative =
        AtnPaths.alternativeTo(classElement, TypeScriptParser.RULE_propertyMemberDeclaration, -1);
    memberKind = first(atn, TypeScriptParser.RULE_propertyMemberDeclaration);
    methodAlternative =
        AtnPaths.alternativeTo(
            memberKind,
            TypeScriptParser.RULE_propertyMemberBase,
            TypeScriptParser.Multiply
        );
    accessorAlternative =
        AtnPaths.alternativeAfter(
            memberKind,
            TypeScriptParser.RULE_propertyMemberBase,
            TypeScriptParser.RULE_getAccessor
        );
    type = first(atn, TypeScriptParser.RULE_type_);
    plainTypeAlternative =
        AtnPaths.alternativeTo(type, TypeScriptParser.RULE_unionOrIntersectionOrPrimaryType, -1);
    ifElse = first(atn, TypeScriptParser.RULE_ifStatement);
    newAlternatives = alternativesOf(primary, TypeScriptParser.New);
    bodies = bodies(atn);
  }

  /** Returns the statement alternative of a reserved keyword, or {@link AtnPaths#NONE}. */
  int statementOf(final int keyword) {
    final Integer rule = STATEMENT_RULES.get(keyword);
    return rule == null ? AtnPaths.NONE : statementAlternatives.getOrDefault(rule, AtnPaths.NONE);
  }

  /** Returns the alternative of a prefix operator, or {@link AtnPaths#NONE}. */
  int operatorOf(final int token) {
    return PREFIX_OPERATORS.contains(token) || token == TypeScriptParser.New
        ? operatorAlternatives.getOrDefault(token, AtnPaths.NONE)
        : AtnPaths.NONE;
  }

  /** Tells whether every decision and alternative that is answered was found in the ATN. */
  boolean complete() {
    return statementAlternatives.keySet().containsAll(STATEMENT_RULES.values())
        && operatorAlternatives.keySet().containsAll(PREFIX_OPERATORS)
        && operatorAlternatives.containsKey(TypeScriptParser.New)
        && !suffixes.isEmpty()
        && newAlternatives.size() >= NEW_FORMS
        && Stream.of(
            functionAlternative,
            memberAlternative,
            methodAlternative,
            accessorAlternative,
            plainTypeAlternative
        )
            .noneMatch(alternative -> alternative == AtnPaths.NONE)
        && bodies.size() >= BODY_RULES.size();
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

  private static DecisionState first(final ATN atn, final int rule) {
    return AtnPaths.firstDecision(atn, atn.ruleToStartState[rule]);
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
        suffixes.add(suffix(atn, predicate));
      }
    }
    return suffixes;
  }

  private static Suffix suffix(final ATN atn, final PrecedencePredicateTransition predicate) {
    return new Suffix(predicate.precedence, atn.nextTokens(predicate.target));
  }

  private static Set<Integer> bodies(final ATN atn) {
    final Set<Integer> bodies = new HashSet<>();
    for (final DecisionState state : atn.decisionToState) {
      if (BODY_RULES.contains(state.ruleIndex)
          && AtnPaths.firstTransition(state.transition(0).target) instanceof AtomTransition atom
          && atom.label == TypeScriptParser.OpenBrace) {
        bodies.add(state.decision);
      }
    }
    return bodies;
  }
}

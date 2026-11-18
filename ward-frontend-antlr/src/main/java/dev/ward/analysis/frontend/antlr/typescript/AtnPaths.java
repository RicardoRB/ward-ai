package dev.ward.analysis.frontend.antlr.typescript;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.atn.ActionTransition;
import org.antlr.v4.runtime.atn.AtomTransition;
import org.antlr.v4.runtime.atn.DecisionState;
import org.antlr.v4.runtime.atn.EpsilonTransition;
import org.antlr.v4.runtime.atn.RuleTransition;
import org.antlr.v4.runtime.atn.StarLoopEntryState;
import org.antlr.v4.runtime.atn.Transition;

/** Finds decisions and alternatives of the TypeScript grammar in its ATN, by rule and token. */
final class AtnPaths {
  /** No alternative was found. */
  static final int NONE = 0;

  private AtnPaths() {}

  static StarLoopEntryState expressionLoop(final ATN atn) {
    for (final DecisionState state : atn.decisionToState) {
      if (state instanceof StarLoopEntryState loop
          && loop.isPrecedenceDecision
          && loop.ruleIndex == TypeScriptParser.RULE_singleExpression) {
        return loop;
      }
    }
    throw new IllegalStateException("the expression rule has no precedence loop");
  }

  /**
   * Returns the alternative of a decision that starts, after any optional part, with a call of the
   * rule and, when {@code token} is not negative, may then continue with that token.
   */
  static int alternativeTo(final DecisionState decision, final int rule, final int token) {
    for (int alt = 0; alt < decision.getNumberOfTransitions(); alt++) {
      for (final RuleTransition call : calls(decision.transition(alt).target)) {
        if (call.ruleIndex == rule && (token < 0 || continuesWith(call.followState, token))) {
          return alt + 1;
        }
      }
    }
    return NONE;
  }

  /**
   * Returns the alternative of a decision that starts with a call of {@code first} and then calls
   * {@code second}, as {@code propertyMemberBase (getAccessor | setAccessor)} does.
   */
  static int alternativeAfter(final DecisionState decision, final int first, final int second) {
    for (int alt = 0; alt < decision.getNumberOfTransitions(); alt++) {
      for (final RuleTransition call : calls(decision.transition(alt).target)) {
        if (call.ruleIndex == first
            && calls(call.followState).stream().anyMatch(next -> next.ruleIndex == second)) {
          return alt + 1;
        }
      }
    }
    return NONE;
  }

  /** Returns the rule calls that {@code start} reaches over plain epsilon transitions. */
  static List<RuleTransition> calls(final ATNState start) {
    final List<RuleTransition> calls = new ArrayList<>();
    final List<ATNState> pending = new ArrayList<>(List.of(start));
    final Set<ATNState> seen = new HashSet<>(pending);
    while (!pending.isEmpty()) {
      for (final Transition transition : pending.remove(pending.size() - 1).getTransitions()) {
        if (transition instanceof RuleTransition call) {
          calls.add(call);
        } else if (passes(transition) && seen.add(transition.target)) {
          pending.add(transition.target);
        }
      }
    }
    return calls;
  }

  static boolean continuesWith(final ATNState start, final int token) {
    final List<ATNState> pending = new ArrayList<>(List.of(start));
    final Set<ATNState> seen = new HashSet<>(pending);
    while (!pending.isEmpty()) {
      for (final Transition transition : pending.remove(pending.size() - 1).getTransitions()) {
        if (transition instanceof AtomTransition atom && atom.label == token) {
          return true;
        }
        if (passes(transition) && seen.add(transition.target)) {
          pending.add(transition.target);
        }
      }
    }
    return false;
  }

  /** Tells whether a transition reads no token, calls no rule and tests no predicate. */
  static boolean passes(final Transition transition) {
    return transition instanceof EpsilonTransition || transition instanceof ActionTransition;
  }

  /** Returns the first decision of a rule: the one its start reaches without a choice. */
  static DecisionState firstDecision(final ATN atn, final ATNState start) {
    ATNState state = start;
    while (!(state instanceof DecisionState) && state.getNumberOfTransitions() == 1) {
      final Transition only = state.transition(0);
      state = only instanceof RuleTransition rule ? rule.followState : only.target;
    }
    if (state instanceof DecisionState decision && decision.ruleIndex == start.ruleIndex) {
      return decision;
    }
    throw new IllegalStateException(
        "no decision at the start of rule " + atn.ruleToStartState[start.ruleIndex]
    );
  }

  /** Returns the first transition from {@code state} that is not a plain epsilon transition. */
  static Transition firstTransition(final ATNState state) {
    ATNState current = state;
    while (current.getNumberOfTransitions() == 1 && passes(current.transition(0))) {
      current = current.transition(0).target;
    }
    return current.transition(0);
  }
}

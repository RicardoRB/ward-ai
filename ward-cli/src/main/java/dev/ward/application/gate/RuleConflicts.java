package dev.ward.application.gate;

import java.util.List;

/**
 * The pairs of rules that contradict each other: no code can pass both. No preset enables both
 * for one language (#685, #695), and a selection that holds both is rejected (#711).
 */
public final class RuleConflicts {
  private static final List<Pair> PAIRS =
      List.of(
          new Pair(
              "ward:java/interface-member-implied-modifier",
              "ward:java/redundant-modifier",
              "one asks for the modifier the other calls redundant"
          ),
          new Pair(
              "ward:java/array-trailing-comma",
              "ward:java/no-array-trailing-comma",
              "one requires the trailing comma, the other forbids it"
          ),
          new Pair(
              "ward:java/call-super-in-constructor",
              "ward:java/avoid-no-argument-super-constructor-call",
              "one requires an explicit super() call, the other forbids it"
          )
      );

  /**
   * One pair of contradicting rule ids and the reason.
   *
   * @param first the first rule id
   * @param second the second rule id
   * @param reason why no code passes both
   */
  public record Pair(String first, String second, String reason) {}

  private RuleConflicts() {}

  /**
   * Returns the pairs.
   *
   * @return the pairs
   */
  public static List<Pair> pairs() {
    return PAIRS;
  }
}

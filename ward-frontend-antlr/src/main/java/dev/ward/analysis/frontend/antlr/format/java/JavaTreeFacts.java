package dev.ward.analysis.frontend.antlr.format.java;

import dev.ward.analysis.domain.format.FormatRule;
import java.util.List;
import java.util.Set;

/**
 * The parse-tree facts the Java layout reads: the rule kinds in pre-order, and the token positions
 * that need a rule's meaning, such as an argument list, an array initializer or an annotation end.
 *
 * @param rules the rule kinds in pre-order
 * @param groupOpens token indexes of the opening parenthesis of a list that breaks one item per
 *     line
 * @param arrayOpens token indexes of the opening brace of an array initializer
 * @param hardAfter token indexes after which a line always breaks: a switch label colon and an enum
 *     constant separator
 * @param annotationEnds token indexes that end an annotation
 * @param angleTokens token indexes of the angle brackets of type arguments and type parameters
 */
record JavaTreeFacts(
    List<FormatRule> rules,
    Set<Integer> groupOpens,
    Set<Integer> arrayOpens,
    Set<Integer> hardAfter,
    Set<Integer> annotationEnds,
    Set<Integer> angleTokens) {

  /** Copies the rules and the token sets. */
  JavaTreeFacts {
    rules = List.copyOf(rules);
    groupOpens = Set.copyOf(groupOpens);
    arrayOpens = Set.copyOf(arrayOpens);
    hardAfter = Set.copyOf(hardAfter);
    annotationEnds = Set.copyOf(annotationEnds);
    angleTokens = Set.copyOf(angleTokens);
  }
}

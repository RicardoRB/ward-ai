package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Counts the distinct types a class depends on, by simple name, through the node kinds the rule
 * names: instantiated types for data abstraction coupling, or every referenced type for fan-out
 * complexity. The members of a nested class belong to that class. A class fails when the count is
 * above the limit.
 */
public final class ClassCouplingCheck implements RuleCheck {

  private static final String CLASS = "classDeclaration";
  private static final Set<String> CLASSES = Set.of(CLASS);
  private static final char GENERICS = '<';
  private static final char PACKAGE_SEPARATOR = '.';

  private final Set<String> typeKinds;
  private final Set<String> excluded;
  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param typeKinds the node kinds whose text names a type
   * @param excluded the simple names that do not count
   * @param max the largest allowed count
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   * @throws IllegalArgumentException if {@code max} is negative
   */
  public ClassCouplingCheck(
      final Set<String> typeKinds,
      final Set<String> excluded,
      final int max,
      final String message) {
    this.typeKinds = Set.copyOf(typeKinds);
    this.excluded = Set.copyOf(excluded);
    this.max = CheckSupport.nonNegative(max, "max");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CLASSES)) {
      report(visit.node(), typesOf(visit.node()), matches);
    }
    return List.copyOf(matches);
  }

  private void report(
      final SyntaxNode node, final Set<String> types, final List<RuleMatch> matches) {
    if (types.size() > max) {
      matches.add(
          new RuleMatch(
              node.span(),
              CheckSupport.fill(message, String.valueOf(types.size()), String.valueOf(max))));
    }
  }

  private Set<String> typesOf(final SyntaxNode node) {
    final Set<String> types = new HashSet<>();
    collect(node, types);
    return types;
  }

  private void collect(final SyntaxNode node, final Set<String> types) {
    for (final SyntaxNode child : node.children()) {
      if (typeKinds.contains(child.kind())) {
        addName(SyntaxWalk.text(child), types);
      }
      if (!CLASS.equals(child.kind())) {
        collect(child, types);
      }
    }
  }

  private void addName(final String text, final Set<String> types) {
    final String withoutGenerics =
        text.indexOf(GENERICS) < 0 ? text : text.substring(0, text.indexOf(GENERICS));
    final String simple =
        withoutGenerics.substring(withoutGenerics.lastIndexOf(PACKAGE_SEPARATOR) + 1);
    if (!simple.isEmpty() && !excluded.contains(simple)) {
      types.add(simple);
    }
  }
}

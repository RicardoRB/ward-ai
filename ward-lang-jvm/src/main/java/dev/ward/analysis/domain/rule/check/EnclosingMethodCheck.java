package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a node by the method that encloses it. In {@code outside} mode it reports each expression
 * whose text matches {@code text} and that is not inside a method named in {@code methods}; in
 * {@code within} mode it reports each node of the given kinds inside a method that has an
 * annotation matching {@code annotation}.
 */
public final class EnclosingMethodCheck implements RuleCheck {

  private static final String METHOD = "methodDeclaration";
  private static final String IDENTIFIER = "identifier";
  private static final String EXPRESSION = "expression";
  private static final String MEMBER = "classBodyDeclaration";

  private final Criteria criteria;
  private final String message;

  /**
   * What a reported node meets.
   *
   * @param kinds the kinds of the nodes that are looked at, {@code expression} when empty
   * @param text a pattern the compact text of the node matches, or {@code null} for any
   * @param methods names of the methods in which the node is allowed, for {@code outside} mode
   * @param annotation a pattern an annotation of the enclosing method matches, for {@code within}
   *     mode, or {@code null} for the {@code outside} mode
   */
  public record Criteria(Set<String> kinds, Pattern text, Set<String> methods, Pattern annotation) {

    /** Copies the sets. */
    public Criteria {
      kinds = kinds.isEmpty() ? Set.of(EXPRESSION) : Set.copyOf(kinds);
      methods = Set.copyOf(methods);
    }
  }

  /**
   * Creates the check.
   *
   * @param criteria what a reported node meets
   * @param message the message reported at the node
   */
  public EnclosingMethodCheck(final Criteria criteria, final String message) {
    this.criteria = Objects.requireNonNull(criteria, "criteria");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, criteria.kinds())) {
      if (textMeets(visit.node()) && contextMeets(visit.ancestors())) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean textMeets(final SyntaxNode node) {
    return criteria.text() == null || criteria.text().matcher(SyntaxWalk.text(node)).find();
  }

  private boolean contextMeets(final List<SyntaxNode> ancestors) {
    final Optional<Integer> method = nearestMethod(ancestors);
    if (criteria.annotation() != null) {
      return method.isPresent() && annotated(ancestors, method.get());
    }
    return method.isEmpty() || !criteria.methods().contains(nameOf(ancestors.get(method.get())));
  }

  private static Optional<Integer> nearestMethod(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if (METHOD.equals(ancestors.get(index).kind())) {
        return Optional.of(index);
      }
    }
    return Optional.empty();
  }

  private static String nameOf(final SyntaxNode method) {
    return method.children().stream()
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }

  private boolean annotated(final List<SyntaxNode> ancestors, final int methodIndex) {
    return ancestors.subList(0, methodIndex).stream()
        .filter(ancestor -> MEMBER.equals(ancestor.kind()))
        .reduce((first, second) -> second)
        .filter(
            member ->
                MemberDeclarations.modifiers(member).stream()
                    .anyMatch(text -> criteria.annotation().matcher(text).find()))
        .isPresent();
  }
}

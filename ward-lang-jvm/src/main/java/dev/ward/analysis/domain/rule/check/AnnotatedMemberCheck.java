package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports each member of a class body that has one of the given annotations, all of the required
 * modifiers and none of the forbidden ones. Annotations are matched by simple name, so
 * {@code @javax.inject.Inject} is {@code Inject}.
 */
public final class AnnotatedMemberCheck implements RuleCheck {

  private static final String MEMBER = "classBodyDeclaration";
  private static final String MEMBER_DECLARATION = "memberDeclaration";
  private static final String MODIFIER = "modifier";
  private static final String ANNOTATION_MARK = "@";

  private final Criteria criteria;
  private final String message;

  /**
   * What a reported member meets.
   *
   * @param declaration the kind of the member's declaration, such as {@code fieldDeclaration}
   * @param annotations simple names of annotations, at least one of which the member has
   * @param modifiers modifiers the member must have
   * @param absentModifiers modifiers the member must not have
   */
  public record Criteria(
      String declaration,
      Set<String> annotations,
      Set<String> modifiers,
      Set<String> absentModifiers) {

    /** Copies the sets. */
    public Criteria {
      Objects.requireNonNull(declaration, "declaration");
      annotations = Set.copyOf(annotations);
      modifiers = Set.copyOf(modifiers);
      absentModifiers = Set.copyOf(absentModifiers);
    }
  }

  /**
   * Creates the check.
   *
   * @param criteria what a reported member meets
   * @param message the message reported at the member
   */
  public AnnotatedMemberCheck(final Criteria criteria, final String message) {
    this.criteria = Objects.requireNonNull(criteria, "criteria");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MEMBER))) {
      final SyntaxNode member = visit.node();
      if (declares(member) && meets(member)) {
        matches.add(CheckSupport.at(member.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean declares(final SyntaxNode member) {
    return member.children().stream()
        .filter(child -> MEMBER_DECLARATION.equals(child.kind()))
        .flatMap(declaration -> declaration.children().stream())
        .anyMatch(child -> criteria.declaration().equals(child.kind()));
  }

  private boolean meets(final SyntaxNode member) {
    final Set<String> keywords = keywords(member);
    return !Collections.disjoint(JUnitMembers.annotations(member), criteria.annotations())
        && keywords.containsAll(criteria.modifiers())
        && Collections.disjoint(keywords, criteria.absentModifiers());
  }

  private static Set<String> keywords(final SyntaxNode member) {
    return member.children().stream()
        .filter(child -> MODIFIER.equals(child.kind()))
        .map(SyntaxWalk::text)
        .filter(text -> !text.startsWith(ANNOTATION_MARK))
        .collect(Collectors.toUnmodifiableSet());
  }
}

package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each secondary constructor that follows another member, when a secondary constructor was
 * already declared before that member, so that the constructors of a class body are grouped
 * together, as Checkstyle's ConstructorsDeclarationGrouping requires.
 */
public final class KotlinConstructorGroupingCheck implements UnitCheck {

  private static final String CLASS_BODY = "classBody";
  private static final String MEMBER = "classMemberDeclaration";
  private static final String CONSTRUCTOR = "secondaryConstructor";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at each misplaced constructor
   */
  public KotlinConstructorGroupingCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CLASS_BODY))) {
      checkBody(visit.node(), matches);
    }
    return List.copyOf(matches);
  }

  private void checkBody(final SyntaxNode body, final List<RuleMatch> matches) {
    boolean constructorSeen = false;
    boolean separated = false;
    for (final SyntaxNode member : KotlinDocs.childrenOf(body, MEMBER)) {
      if (isConstructor(member)) {
        if (separated) {
          matches.add(CheckSupport.at(member.span(), message));
        }
        constructorSeen = true;
      } else {
        separated |= constructorSeen;
      }
    }
  }

  private static boolean isConstructor(final SyntaxNode member) {
    return member.children().stream().anyMatch(child -> CONSTRUCTOR.equals(child.kind()));
  }
}

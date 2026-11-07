package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each constructor that follows other members which themselves follow a constructor, so
 * that the constructors of a body are grouped together, as Checkstyle's
 * ConstructorsDeclarationGrouping does. With the user option, constructors must also be in
 * increasing order of parameter count.
 */
public final class ConstructorsDeclarationGroupingCheck implements UnitCheck {

  private static final Set<String> BODIES =
      Set.of("classBody", "recordBody", "enumBodyDeclarations");
  private static final Set<String> NOT_ENTERED = Set.of("block", "classBody", "interfaceBody");
  private static final String CONSTRUCTOR = "constructorDeclaration";
  private static final String COMPACT = "compactConstructorDeclaration";
  private static final String SEMICOLON = "';'";
  private static final String MEMBER = "classBodyDeclaration";

  private final boolean orderByArity;
  private final String message;

  /**
   * Creates the check.
   *
   * @param orderByArity whether constructors must be in increasing order of parameter count
   * @param message the message reported at each misplaced constructor
   */
  public ConstructorsDeclarationGroupingCheck(final boolean orderByArity, final String message) {
    this.orderByArity = orderByArity;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, BODIES)) {
      checkBody(visit.node(), matches);
    }
    return List.copyOf(matches);
  }

  private void checkBody(final SyntaxNode body, final List<RuleMatch> matches) {
    boolean constructorSeen = false;
    boolean separated = false;
    int previousArity = -1;
    for (final SyntaxNode member : body.children()) {
      if (isEmptyMember(member) || SyntaxWalk.COMMENT_KIND.equals(member.kind())) {
        continue;
      }
      final Optional<SyntaxNode> constructor = constructorOf(member);
      if (constructor.isEmpty()) {
        separated |= constructorSeen;
        continue;
      }
      final int arity = parameters(constructor.get());
      if (separated || orderByArity && arity < previousArity) {
        matches.add(CheckSupport.at(member.span(), message));
      }
      previousArity = arity;
      constructorSeen = true;
    }
  }

  private static boolean isEmptyMember(final SyntaxNode member) {
    return SEMICOLON.equals(member.kind())
        || MEMBER.equals(member.kind())
            && member.children().size() == 1
            && SEMICOLON.equals(member.children().get(0).kind());
  }

  private static Optional<SyntaxNode> constructorOf(final SyntaxNode member) {
    return COMPACT.equals(member.kind())
        ? Optional.of(member)
        : SyntaxWalk.firstDescendant(member, CONSTRUCTOR, NOT_ENTERED);
  }

  private static int parameters(final SyntaxNode constructor) {
    return SyntaxWalk.firstDescendant(constructor, "formalParameters", NOT_ENTERED)
        .map(
            formals ->
                (int)
                    SyntaxWalk.descendants(formals).stream()
                        .filter(node -> "formalParameter".equals(node.kind()))
                        .count())
        .orElse(0);
  }
}

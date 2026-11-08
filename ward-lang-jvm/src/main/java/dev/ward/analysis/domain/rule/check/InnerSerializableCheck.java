package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a serializable inner (non-static member) class. Serializing it also serializes the
 * instance around it. In {@code outer-serializable} mode the outer class implements Serializable
 * itself; otherwise the outer class is known not to be serializable, so serializing the inner class
 * fails. An outer class that extends another class is left alone, since its superclass may be
 * serializable.
 */
public final class InnerSerializableCheck implements RuleCheck {

  private static final String MEMBER = "classBodyDeclaration";
  private static final String MEMBER_DECLARATION = "memberDeclaration";
  private static final String CLASS_BODY = "classBody";
  private static final int MEMBER_DEPTH = 4;
  private static final int BODY_OFFSET = 3;

  private final boolean outerSerializable;
  private final String message;

  /**
   * Creates the check.
   *
   * @param outerSerializable whether the outer class must be serializable, or known not to be
   * @param message the message reported at the inner class
   */
  public InnerSerializableCheck(final boolean outerSerializable, final String message) {
    this.outerSerializable = outerSerializable;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      final SyntaxNode outer = outerOf(visit.ancestors());
      if (outer != null && ClassHeaders.implementsSerializable(visit.node()) && outerMeets(outer)) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean outerMeets(final SyntaxNode outer) {
    if (outerSerializable) {
      return ClassHeaders.implementsSerializable(outer);
    }
    return !ClassHeaders.implementsSerializable(outer) && ClassHeaders.extendsType(outer).isEmpty();
  }

  private static SyntaxNode outerOf(final List<SyntaxNode> up) {
    final int size = up.size();
    final boolean instanceMember =
        size >= MEMBER_DEPTH
            && MEMBER_DECLARATION.equals(up.get(size - 1).kind())
            && MEMBER.equals(up.get(size - 2).kind())
            && CLASS_BODY.equals(up.get(size - BODY_OFFSET).kind())
            && MemberDeclarations.CLASS.equals(up.get(size - MEMBER_DEPTH).kind())
            && !MemberDeclarations.modifiers(up.get(size - 2)).contains(MemberDeclarations.STATIC);
    return instanceMember ? up.get(size - MEMBER_DEPTH) : null;
  }
}

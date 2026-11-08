package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports an inner class, or an anonymous class, that never uses the instance of the class around
 * it. It could be static, or a named static class. Only classes directly inside a top-level class
 * with no superclass are checked, so that every name the inner class could take from the outer one
 * is known; a class that mentions any such name, a type parameter of the outer class or the outer
 * instance explicitly is treated as using it.
 */
public final class InnerClassUseCheck implements RuleCheck {

  private static final Set<String> TYPES =
      Set.of(
          "classDeclaration",
          "interfaceDeclaration",
          "enumDeclaration",
          "recordDeclaration",
          "classCreatorRest");
  private static final String CREATOR_REST = "classCreatorRest";
  private static final String IDENTIFIER = "identifier";
  private static final String TYPE_PARAMETER = "typeParameter";
  private static final String CLASS_BODY = "classBody";
  private static final String MEMBER = "classBodyDeclaration";
  private static final String MEMBER_DECLARATION = "memberDeclaration";
  private static final int MEMBER_DEPTH = 4;
  private static final int BODY_OFFSET = 3;

  private final boolean anonymous;
  private final String message;

  /**
   * Creates the check.
   *
   * @param anonymous whether to look at anonymous classes instead of member classes
   * @param message the message reported at the class
   */
  public InnerClassUseCheck(final boolean anonymous, final String message) {
    this.anonymous = anonymous;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    final Set<String> kinds = anonymous ? Set.of(CREATOR_REST) : Set.of(MemberDeclarations.CLASS);
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, kinds)) {
      final SyntaxNode outer = outerOf(visit);
      final SyntaxNode body = bodyOf(visit.node());
      if (outer != null && body != null && !usesOuter(outer, body)) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  /** Returns the top-level class around the node when the node is an instance inner class. */
  private SyntaxNode outerOf(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> up = visit.ancestors();
    final List<SyntaxNode> types = up.stream().filter(node -> TYPES.contains(node.kind())).toList();
    if (types.size() != 1
        || !MemberDeclarations.CLASS.equals(types.get(0).kind())
        || ClassHeaders.extendsType(types.get(0)).isPresent()) {
      return null;
    }
    final boolean instance = anonymous ? !FieldScopes.staticContext(up) : isInstanceMember(up);
    return instance ? types.get(0) : null;
  }

  private static boolean isInstanceMember(final List<SyntaxNode> up) {
    final int size = up.size();
    return size >= MEMBER_DEPTH
        && MEMBER_DECLARATION.equals(up.get(size - 1).kind())
        && MEMBER.equals(up.get(size - 2).kind())
        && CLASS_BODY.equals(up.get(size - BODY_OFFSET).kind())
        && !MemberDeclarations.modifiers(up.get(size - 2)).contains(MemberDeclarations.STATIC);
  }

  private static SyntaxNode bodyOf(final SyntaxNode node) {
    return node.children().stream()
        .filter(child -> CLASS_BODY.equals(child.kind()))
        .findFirst()
        .orElse(null);
  }

  private static boolean usesOuter(final SyntaxNode outer, final SyntaxNode inner) {
    final Set<String> names = outerNames(outer);
    final String text = SyntaxWalk.text(inner);
    return text.contains(ClassHeaders.name(outer) + ".this")
        || SyntaxWalk.descendants(inner).stream()
            .filter(node -> IDENTIFIER.equals(node.kind()))
            .anyMatch(node -> names.contains(SyntaxWalk.text(node)));
  }

  private static Set<String> outerNames(final SyntaxNode outer) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxNode member : ClassMembers.members(outer)) {
      final SyntaxNode declaration = ClassMembers.declarationOf(member);
      if (!MemberDeclarations.modifiers(member).contains(MemberDeclarations.STATIC)) {
        names.addAll(ClassMembers.fieldNames(declaration));
        names.add(MemberDeclarations.name(declaration));
      }
    }
    SyntaxWalk.descendants(outer).stream()
        .filter(node -> TYPE_PARAMETER.equals(node.kind()))
        .flatMap(node -> node.children().stream())
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .forEach(child -> names.add(SyntaxWalk.text(child)));
    return names;
  }
}

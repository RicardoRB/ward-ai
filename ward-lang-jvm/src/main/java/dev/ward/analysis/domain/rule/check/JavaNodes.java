package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Small readers of the Java syntax tree shared by the code style checks. */
final class JavaNodes {

  static final String IDENTIFIER = "identifier";
  static final String BLOCK_STATEMENT = "blockStatement";
  static final String LOCAL_VARIABLE = "localVariableDeclaration";
  static final String CLASS_BODY = "classBody";
  static final Set<String> TYPE_KINDS =
      Set.of("classDeclaration", "interfaceDeclaration", "enumDeclaration", "recordDeclaration");

  private static final String MEMBER_DECLARATION = "memberDeclaration";
  private static final Set<String> MODIFIER_KINDS =
      Set.of("modifier", "classOrInterfaceModifier", "annotation");

  private JavaNodes() {}

  /** Returns the direct children of {@code node} that have the given kind. */
  static List<SyntaxNode> kids(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).toList();
  }

  /** Returns the first direct child of {@code kind}. */
  static Optional<SyntaxNode> kid(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).findFirst();
  }

  /** Returns the text of the first identifier child, or an empty string. */
  static String name(final SyntaxNode declaration) {
    return kid(declaration, IDENTIFIER).map(SyntaxWalk::text).orElse("");
  }

  /** Returns the modifier texts of the declaration that {@code visit} reaches. */
  static List<String> modifiers(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> up = visit.ancestors();
    if (up.isEmpty()) {
      return List.of();
    }
    final SyntaxNode parent = up.get(up.size() - 1);
    return holder(parent, up).children().stream()
        .filter(child -> MODIFIER_KINDS.contains(child.kind()))
        .map(SyntaxWalk::text)
        .toList();
  }

  private static SyntaxNode holder(final SyntaxNode parent, final List<SyntaxNode> up) {
    final boolean member = MEMBER_DECLARATION.equals(parent.kind()) && up.size() > 1;
    if (member) {
      return up.get(up.size() - 2);
    }
    return parent;
  }

  /** Returns the block statements of {@code block}. */
  static List<SyntaxNode> statements(final SyntaxNode block) {
    return kids(block, BLOCK_STATEMENT);
  }

  /** Returns the member declarations (method, constructor, field) of a class body, with holders. */
  static List<Member> members(final SyntaxNode type) {
    return kid(type, CLASS_BODY).map(JavaNodes::membersOf).orElse(List.of());
  }

  private static List<Member> membersOf(final SyntaxNode body) {
    return kids(body, "classBodyDeclaration").stream()
        .flatMap(
            holder ->
                kid(holder, MEMBER_DECLARATION)
                    .flatMap(m -> m.children().stream().findFirst())
                    .map(node -> new Member(holder, node))
                    .stream())
        .toList();
  }

  /**
   * A member of a class body.
   *
   * @param holder the class body declaration with the member's modifiers
   * @param node the method, constructor or field node
   */
  record Member(SyntaxNode holder, SyntaxNode node) {

    /** Tells whether the member has the modifier. */
    boolean has(final String modifier) {
      return holder.children().stream()
          .filter(child -> MODIFIER_KINDS.contains(child.kind()))
          .anyMatch(child -> modifier.equals(SyntaxWalk.text(child)));
    }
  }
}

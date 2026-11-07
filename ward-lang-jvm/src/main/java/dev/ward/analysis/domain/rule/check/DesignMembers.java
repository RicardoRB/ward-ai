package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the parts of Java type declarations that the design checks compare: the modifiers and
 * annotations of a declaration, its members and the names of its fields.
 */
final class DesignMembers {

  /** The kinds of the declarations of types. */
  static final Set<String> TYPE_KINDS =
      Set.of(
          "classDeclaration",
          "interfaceDeclaration",
          "enumDeclaration",
          "recordDeclaration",
          "annotationTypeDeclaration");

  private static final String TYPE_DECLARATION = "typeDeclaration";
  private static final String CLASS_BODY_DECLARATION = "classBodyDeclaration";
  private static final String INTERFACE_BODY_DECLARATION = "interfaceBodyDeclaration";
  private static final String MEMBER_DECLARATION = "memberDeclaration";
  private static final String INTERFACE_MEMBER_DECLARATION = "interfaceMemberDeclaration";
  private static final String MODIFIER = "modifier";
  private static final String CLASSIFIER_MODIFIER = "classOrInterfaceModifier";
  private static final String GENERIC_PREFIX = "generic";
  private static final String CLASS_BODY = "classBody";
  private static final String INTERFACE_BODY = "interfaceBody";
  private static final String JAVADOC_OPEN = "/**";

  private static final Set<String> BODY_WRAPPERS =
      Set.of(CLASS_BODY_DECLARATION, INTERFACE_BODY_DECLARATION);
  private static final Set<String> MEMBER_HOLDERS =
      Set.of(MEMBER_DECLARATION, INTERFACE_MEMBER_DECLARATION);

  private DesignMembers() {}

  /**
   * A member of a type body: the modifier texts that open it (annotations included, as
   * {@code @Name(...)}), its declaration node, and the Javadoc comment right before it, or an empty
   * text.
   */
  record Member(List<String> modifiers, SyntaxNode declaration, String javadoc) {

    /** Returns whether the member has the modifier {@code word}. */
    boolean has(final String word) {
      return modifiers.contains(word);
    }

    /** Returns whether the member has a Javadoc comment right before it. */
    boolean documented() {
      return !javadoc.isEmpty();
    }

    /** Returns the simple names of the annotations on the member. */
    Set<String> annotations() {
      return DeclarationText.annotationNames(modifiers);
    }
  }

  /** Returns the visits of the type declarations of {@code root}. */
  static List<SyntaxWalk.Visit> types(final SyntaxNode root) {
    return SyntaxWalk.visits(root, TYPE_KINDS);
  }

  /** Tells whether a declaration is a top-level type, outside every other type and body. */
  static boolean topLevel(final SyntaxWalk.Visit visit) {
    return visit.ancestors().stream()
        .allMatch(
            ancestor ->
                "compilationunit".equals(ancestor.kind())
                    || TYPE_DECLARATION.equals(ancestor.kind()));
  }

  /** Returns the modifier texts that open the declaration {@code visit}, or none. */
  static List<String> modifiers(final SyntaxWalk.Visit visit) {
    return wrapper(visit.ancestors()).map(DesignMembers::modifierTexts).orElse(List.of());
  }

  /** Returns the members of a class or interface body, without initializer blocks. */
  static List<Member> members(final SyntaxNode body) {
    final List<Member> members = new ArrayList<>();
    final List<SyntaxNode> children = body.children();
    for (int index = 0; index < children.size(); index++) {
      final SyntaxNode wrapper = children.get(index);
      if (BODY_WRAPPERS.contains(wrapper.kind())) {
        addMembers(wrapper, javadocBefore(children, index), members);
      }
    }
    return List.copyOf(members);
  }

  /** Returns the class or interface body of a type declaration, when it has one. */
  static Optional<SyntaxNode> body(final SyntaxNode type) {
    return type.children().stream()
        .filter(child -> CLASS_BODY.equals(child.kind()) || INTERFACE_BODY.equals(child.kind()))
        .findFirst();
  }

  private static void addMembers(
      final SyntaxNode wrapper, final String javadoc, final List<Member> members) {
    final List<String> modifiers = modifierTexts(wrapper);
    for (final SyntaxNode holder : wrapper.children()) {
      if (MEMBER_HOLDERS.contains(holder.kind())) {
        holder.children().stream()
            .map(DesignMembers::unwrapGeneric)
            .forEach(declaration -> members.add(new Member(modifiers, declaration, javadoc)));
      }
    }
  }

  /** Returns the text of the comment before {@code index}, when it is a Javadoc comment. */
  private static String javadocBefore(final List<SyntaxNode> children, final int index) {
    if (index == 0 || !SyntaxWalk.COMMENT_KIND.equals(children.get(index - 1).kind())) {
      return "";
    }
    final String text = children.get(index - 1).fields().getOrDefault("text", "");
    return text.startsWith(JAVADOC_OPEN) ? text : "";
  }

  /** A generic declaration wraps the plain declaration as its last child. */
  private static SyntaxNode unwrapGeneric(final SyntaxNode declaration) {
    if (declaration.kind().startsWith(GENERIC_PREFIX) && !declaration.children().isEmpty()) {
      return declaration.children().get(declaration.children().size() - 1);
    }
    return declaration;
  }

  /**
   * Returns the node that holds the modifiers of the declaration at the end of {@code ancestors}: a
   * type's own declaration, or the body wrapper of a member.
   */
  private static Optional<SyntaxNode> wrapper(final List<SyntaxNode> ancestors) {
    int index = ancestors.size() - 1;
    while (index >= 0 && ancestors.get(index).kind().startsWith(GENERIC_PREFIX)) {
      index--;
    }
    if (index < 0) {
      return Optional.empty();
    }
    final SyntaxNode last = ancestors.get(index);
    if (TYPE_DECLARATION.equals(last.kind())) {
      return Optional.of(last);
    }
    final boolean holder = MEMBER_HOLDERS.contains(last.kind());
    if (holder && index > 0 && BODY_WRAPPERS.contains(ancestors.get(index - 1).kind())) {
      return Optional.of(ancestors.get(index - 1));
    }
    return Optional.empty();
  }

  private static List<String> modifierTexts(final SyntaxNode wrapper) {
    return wrapper.children().stream()
        .filter(child -> MODIFIER.equals(child.kind()) || CLASSIFIER_MODIFIER.equals(child.kind()))
        .map(SyntaxWalk::text)
        .toList();
  }
}

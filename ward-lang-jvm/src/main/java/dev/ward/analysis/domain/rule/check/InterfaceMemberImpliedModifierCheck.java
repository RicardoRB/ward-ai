package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports an interface member that lacks a modifier the compiler implies: {@code public} and {@code
 * abstract} on methods, {@code public}, {@code static} and {@code final} on fields, and {@code
 * public} and {@code static} on nested types. Each check runs only when its flag holds.
 */
public final class InterfaceMemberImpliedModifierCheck implements RuleCheck {

  private static final String INTERFACE_BODY = "interfaceBodyDeclaration";
  private static final String MEMBER = "interfaceMemberDeclaration";
  private static final Set<String> MODIFIER_KINDS =
      Set.of("modifier", "classOrInterfaceModifier", "interfaceMethodModifier");
  private static final Set<String> METHOD_KINDS =
      Set.of("interfaceMethodDeclaration", "genericInterfaceMethodDeclaration");
  private static final Set<String> NESTED_TYPES =
      Set.of("classDeclaration", "interfaceDeclaration", "enumDeclaration", "recordDeclaration");
  private static final String CONSTANT = "constDeclaration";
  private static final String PUBLIC = "public";
  private static final String STATIC = "static";
  private static final String FINAL = "final";
  private static final String ABSTRACT = "abstract";
  private static final Set<String> NOT_ABSTRACT = Set.of("default", "static", "private");

  private final Flags flags;
  private final String message;

  /**
   * Which implied modifiers are enforced.
   *
   * @param abstractMethod whether a body-less method must say {@code abstract}
   * @param finalField whether a constant must say {@code final}
   * @param publicField whether a constant must say {@code public}
   * @param publicMethod whether a method that is not private must say {@code public}
   * @param publicNested whether a nested type must say {@code public}
   * @param staticField whether a constant must say {@code static}
   * @param staticNested whether a nested type must say {@code static}
   */
  public record Flags(
      boolean abstractMethod,
      boolean finalField,
      boolean publicField,
      boolean publicMethod,
      boolean publicNested,
      boolean staticField,
      boolean staticNested) {}

  /**
   * Creates the check.
   *
   * @param flags which implied modifiers are enforced
   * @param message the message
   */
  public InterfaceMemberImpliedModifierCheck(final Flags flags, final String message) {
    this.flags = Objects.requireNonNull(flags, "flags");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      if (INTERFACE_BODY.equals(node.kind()) && !node.children().isEmpty()) {
        checkMember(node, matches);
      }
    }
    return List.copyOf(matches);
  }

  private void checkMember(final SyntaxNode holder, final List<RuleMatch> matches) {
    final List<String> modifiers = modifierTexts(holder);
    for (final SyntaxNode member : holder.children()) {
      if (MEMBER.equals(member.kind()) && !member.children().isEmpty()) {
        final SyntaxNode declaration = member.children().get(0);
        if (isBad(declaration, modifiers)) {
          matches.add(CheckSupport.at(holder.children().get(0).span(), message));
        }
      }
    }
  }

  private boolean isBad(final SyntaxNode declaration, final List<String> holderModifiers) {
    final List<String> modifiers = new ArrayList<>(holderModifiers);
    if (METHOD_KINDS.contains(declaration.kind())) {
      modifiers.addAll(modifierTexts(declaration));
      return isBadMethod(declaration, modifiers);
    }
    if (CONSTANT.equals(declaration.kind())) {
      return isBadConstant(modifiers);
    }
    return NESTED_TYPES.contains(declaration.kind()) && isBadNested(modifiers);
  }

  private boolean isBadConstant(final List<String> modifiers) {
    return (flags.publicField() && !modifiers.contains(PUBLIC))
        || (flags.staticField() && !modifiers.contains(STATIC))
        || (flags.finalField() && !modifiers.contains(FINAL));
  }

  private boolean isBadNested(final List<String> modifiers) {
    return (flags.publicNested() && !modifiers.contains(PUBLIC))
        || (flags.staticNested() && !modifiers.contains(STATIC));
  }

  private boolean isBadMethod(final SyntaxNode method, final List<String> modifiers) {
    final boolean isPrivate = modifiers.contains("private");
    final boolean publicMissing = flags.publicMethod() && !isPrivate && !modifiers.contains(PUBLIC);
    final boolean bodyless =
        SyntaxWalk.descendants(method).stream()
            .anyMatch(node -> "methodBody".equals(node.kind()) && hasSemicolon(node));
    final boolean abstractMissing =
        flags.abstractMethod()
            && bodyless
            && modifiers.stream().noneMatch(NOT_ABSTRACT::contains)
            && !modifiers.contains(ABSTRACT);
    return publicMissing || abstractMissing;
  }

  private static boolean hasSemicolon(final SyntaxNode body) {
    return body.children().stream().anyMatch(child -> "';'".equals(child.kind()));
  }

  private static List<String> modifierTexts(final SyntaxNode node) {
    return node.children().stream()
        .filter(child -> MODIFIER_KINDS.contains(child.kind()))
        .map(child -> SyntaxWalk.text(child).trim())
        .toList();
  }
}

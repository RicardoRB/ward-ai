package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a nested enum, interface or record of a class that lacks the implied {@code static}
 * modifier. Each kind is checked only when its flag holds, as Checkstyle's options do.
 */
public final class ClassMemberImpliedModifierCheck implements RuleCheck {

  private static final String CLASS_BODY = "classBodyDeclaration";
  private static final Set<String> MODIFIER_KINDS = Set.of("modifier", "classOrInterfaceModifier");
  private static final String STATIC = "static";

  private final Map<String, Boolean> flags;
  private final String message;

  /**
   * Creates the check.
   *
   * @param enumFlag whether a nested enum must say {@code static}
   * @param interfaceFlag whether a nested interface must say {@code static}
   * @param recordFlag whether a nested record must say {@code static}
   * @param message the message
   */
  public ClassMemberImpliedModifierCheck(
      final boolean enumFlag,
      final boolean interfaceFlag,
      final boolean recordFlag,
      final String message) {
    this.flags =
        Map.of(
            "enumDeclaration", enumFlag,
            "interfaceDeclaration", interfaceFlag,
            "recordDeclaration", recordFlag);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      if (CLASS_BODY.equals(node.kind()) && isBad(node)) {
        matches.add(CheckSupport.at(node.children().get(0).span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean isBad(final SyntaxNode member) {
    final List<SyntaxNode> declarations = new ArrayList<>();
    for (final SyntaxNode child : member.children()) {
      if (child.kind().toLowerCase(java.util.Locale.ROOT).endsWith("memberdeclaration")) {
        declarations.addAll(child.children());
      }
    }
    return declarations.stream()
        .filter(declaration -> flags.getOrDefault(declaration.kind(), false))
        .anyMatch(declaration -> !hasStatic(member));
  }

  private static boolean hasStatic(final SyntaxNode member) {
    return member.children().stream()
        .filter(child -> MODIFIER_KINDS.contains(child.kind()))
        .anyMatch(child -> STATIC.equals(SyntaxWalk.text(child).trim()));
  }
}

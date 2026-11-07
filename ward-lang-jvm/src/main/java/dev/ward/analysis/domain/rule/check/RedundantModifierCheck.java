package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports modifiers the language already implies: {@code public} and {@code abstract} on interface
 * methods, {@code public}, {@code static} and {@code final} on interface constants, and {@code
 * static} on nested interfaces, enums and records.
 */
public final class RedundantModifierCheck implements RuleCheck {

  private static final String INTERFACE_BODY = "interfaceBodyDeclaration";
  private static final String CLASS_BODY = "classBodyDeclaration";
  private static final Set<String> MODIFIER_KINDS =
      Set.of("modifier", "classOrInterfaceModifier", "interfaceMethodModifier");
  private static final Set<String> METHOD_KINDS =
      Set.of("interfaceMethodDeclaration", "genericInterfaceMethodDeclaration");
  private static final Set<String> NESTED_TYPES =
      Set.of("interfaceDeclaration", "enumDeclaration", "recordDeclaration");
  private static final Set<String> METHOD_REDUNDANT = Set.of("public", "abstract");
  private static final Set<String> CONSTANT_REDUNDANT = Set.of("public", "static", "final");
  private static final String STATIC = "static";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message
   */
  public RedundantModifierCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      if (INTERFACE_BODY.equals(node.kind()) || CLASS_BODY.equals(node.kind())) {
        checkMember(node, matches);
      }
    }
    return List.copyOf(matches);
  }

  private void checkMember(final SyntaxNode holder, final List<RuleMatch> matches) {
    final boolean inInterface = INTERFACE_BODY.equals(holder.kind());
    final List<SyntaxNode> modifiers =
        holder.children().stream().filter(child -> MODIFIER_KINDS.contains(child.kind())).toList();
    for (final SyntaxNode member : holder.children()) {
      if (!member.kind().toLowerCase(java.util.Locale.ROOT).endsWith("memberdeclaration")) {
        continue;
      }
      final SyntaxNode declaration =
          member.children().isEmpty() ? member : member.children().get(0);
      if (inInterface && METHOD_KINDS.contains(declaration.kind())) {
        report(modifiers, METHOD_REDUNDANT, matches);
        report(interfaceMethodModifiers(declaration), METHOD_REDUNDANT, matches);
      } else if (inInterface && "constDeclaration".equals(declaration.kind())) {
        report(modifiers, CONSTANT_REDUNDANT, matches);
      } else if (NESTED_TYPES.contains(declaration.kind())) {
        report(modifiers, Set.of(STATIC), matches);
      }
    }
  }

  private static List<SyntaxNode> interfaceMethodModifiers(final SyntaxNode method) {
    return method.children().stream()
        .filter(child -> "interfaceMethodModifier".equals(child.kind()))
        .toList();
  }

  private void report(
      final List<SyntaxNode> items, final Set<String> redundant, final List<RuleMatch> matches) {
    items.stream()
        .filter(item -> redundant.contains(SyntaxWalk.text(item).trim()))
        .forEach(item -> matches.add(CheckSupport.at(item.span(), message)));
  }
}

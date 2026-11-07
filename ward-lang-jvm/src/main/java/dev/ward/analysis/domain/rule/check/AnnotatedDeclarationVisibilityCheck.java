package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a declaration with one of the {@code annotations} whose visibility is not one of the
 * {@code visibility} keywords. A declaration without an access keyword is package-private.
 */
public final class AnnotatedDeclarationVisibilityCheck implements RuleCheck {

  private static final String PACKAGE = "package";
  private static final Set<String> ACCESS = Set.of("public", "protected", "private");
  private static final Set<String> MODIFIER_KINDS =
      Set.of("modifier", "classOrInterfaceModifier", "variableModifier", "interfaceMethodModifier");

  private final Set<String> annotations;
  private final Set<String> visibility;
  private final String message;

  /**
   * Creates the check.
   *
   * @param annotations the annotations, by simple or qualified name
   * @param visibility the access levels that are allowed: {@code public}, {@code protected}, {@code
   *     package} or {@code private}
   * @param message the message
   */
  public AnnotatedDeclarationVisibilityCheck(
      final Set<String> annotations, final Set<String> visibility, final String message) {
    this.annotations = Set.copyOf(annotations);
    this.visibility = Set.copyOf(visibility);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      if (MODIFIER_KINDS.contains(node.kind())) {
        continue;
      }
      final List<SyntaxNode> items = modifierItems(node);
      final String access = access(items);
      for (final SyntaxNode item : items) {
        if (isWatched(SyntaxWalk.text(item)) && !visibility.contains(access)) {
          matches.add(CheckSupport.at(item.span(), message));
        }
      }
    }
    return List.copyOf(matches);
  }

  private static List<SyntaxNode> modifierItems(final SyntaxNode node) {
    return node.children().stream().filter(child -> MODIFIER_KINDS.contains(child.kind())).toList();
  }

  private static String access(final List<SyntaxNode> items) {
    return items.stream()
        .map(item -> SyntaxWalk.text(item).trim())
        .filter(ACCESS::contains)
        .findFirst()
        .orElse(PACKAGE);
  }

  private boolean isWatched(final String text) {
    final String trimmed = text.trim();
    if (!trimmed.startsWith("@")) {
      return false;
    }
    final String name = trimmed.substring(1).split("\\(", 2)[0].trim();
    return annotations.stream().anyMatch(full -> full.equals(name) || full.endsWith("." + name));
  }
}

package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Fails each import whose last name appears nowhere else in the unit. A word in a comment counts as
 * a reference, so a name used only in Javadoc is not reported. A star import is not checked.
 */
public final class UnreferencedNameCheck implements RuleCheck {

  private static final String IMPORT_KIND = "importDeclaration";
  private static final String NAME_KIND = "identifier";
  private static final String STAR = "*";
  private static final String WORD_SEPARATOR = "\\W+";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the unused name
   */
  public UnreferencedNameCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<String> used = new HashSet<>();
    collectUsed(root, used);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(IMPORT_KIND))) {
      final SyntaxNode node = visit.node();
      final Optional<String> name = lastName(node);
      if (name.isPresent() && !used.contains(name.get()) && !isStar(node)) {
        matches.add(new RuleMatch(node.span(), CheckSupport.fill(message, name.get(), "")));
      }
    }
    return List.copyOf(matches);
  }

  private static Optional<String> lastName(final SyntaxNode importNode) {
    final List<SyntaxNode> names = new ArrayList<>();
    SyntaxWalk.descendants(importNode).stream()
        .filter(d -> NAME_KIND.equals(d.kind()))
        .forEach(names::add);
    if (names.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(SyntaxWalk.text(names.get(names.size() - 1)));
  }

  private static boolean isStar(final SyntaxNode importNode) {
    return SyntaxWalk.leaves(importNode).stream()
        .anyMatch(leaf -> STAR.equals(leaf.fields().get("text")));
  }

  /** Collects the token texts and comment words outside import declarations. */
  private static void collectUsed(final SyntaxNode node, final Set<String> used) {
    if (IMPORT_KIND.equals(node.kind())) {
      return;
    }
    if (SyntaxWalk.COMMENT_KIND.equals(node.kind())) {
      for (final String word : node.fields().get("text").split(WORD_SEPARATOR)) {
        used.add(word);
      }
      return;
    }
    if (node.children().isEmpty()) {
      used.add(node.fields().getOrDefault("text", ""));
      return;
    }
    node.children().forEach(child -> collectUsed(child, used));
  }
}

package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a KDoc that does not document a class, object, function, property, enum entry, typealias,
 * secondary constructor or value parameter, or that sits inside such a declaration, as ktlint's
 * {@code kdoc} rule does. The tree places a comment before the node that follows it, so a KDoc is
 * allowed when the first declaration after it, through the wrapper nodes, is one of those kinds.
 */
public final class KotlinKdocPlacementCheck implements RuleCheck {

  private static final String COMMENT = SyntaxWalk.COMMENT_KIND;
  private static final Set<String> WRAPPERS =
      Set.of("topLevelObject", "classMemberDeclaration", "declaration", "statement");
  private static final Set<String> DOCUMENTABLE =
      Set.of(
          "classDeclaration",
          "objectDeclaration",
          "functionDeclaration",
          "propertyDeclaration",
          "typeAlias",
          "enumEntry",
          "secondaryConstructor",
          "functionValueParameter",
          "classParameter",
          "parameter");
  private static final Set<String> SKIPPED =
      Set.of(KotlinLeaves.NEWLINE, "anysemi", COMMENT, "preamble", "packageHeader", "importList");

  /** Containers whose trailing comments belong to the declaration that follows the container. */
  private static final Set<String> CLIMBABLE =
      Set.of(
          "classMemberDeclaration", "semi", "anysemi", "packageHeader", "importList", "preamble");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the KDoc
   */
  public KotlinKdocPlacementCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(COMMENT))) {
      if (KotlinLeaves.isKDoc(visit.node()) && !isPlaced(visit)) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  /**
   * Climbs from the comment through trailing containers: a comment after the last declaration of a
   * member or after the package line is placed inside that container, so the declaration it
   * documents is the next sibling of the container.
   */
  private static boolean isPlaced(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> ancestors = visit.ancestors();
    SyntaxNode node = visit.node();
    for (int level = ancestors.size() - 1; level >= 0; level--) {
      final SyntaxNode parent = ancestors.get(level);
      if (DOCUMENTABLE.contains(parent.kind())) {
        return false;
      }
      final Optional<SyntaxNode> next = nextSignificant(parent.children(), node);
      if (next.isPresent()) {
        return isDocumentable(next.get());
      }
      if (!CLIMBABLE.contains(parent.kind())) {
        return false;
      }
      node = parent;
    }
    return false;
  }

  private static Optional<SyntaxNode> nextSignificant(
      final List<SyntaxNode> siblings, final SyntaxNode node) {
    for (int index = siblings.indexOf(node) + 1; index < siblings.size(); index++) {
      if (!SKIPPED.contains(siblings.get(index).kind())) {
        return Optional.of(siblings.get(index));
      }
    }
    return Optional.empty();
  }

  private static boolean isDocumentable(final SyntaxNode node) {
    SyntaxNode current = node;
    while (WRAPPERS.contains(current.kind())) {
      final List<SyntaxNode> children =
          current.children().stream().filter(child -> !SKIPPED.contains(child.kind())).toList();
      if (children.isEmpty()) {
        return false;
      }
      current = children.get(0);
    }
    return DOCUMENTABLE.contains(current.kind());
  }
}

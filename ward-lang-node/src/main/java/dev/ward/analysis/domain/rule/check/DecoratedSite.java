package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/**
 * A node under check with its nearest enclosing class, class element and class member, found from
 * its ancestors. The class element holds the member's decorators; the member holds its name.
 */
final class DecoratedSite {

  static final String CLASS = "classDeclaration";
  static final String CLASS_ELEMENT = "classElement";
  static final String MEMBER = "propertyMemberDeclaration";
  static final String PROPERTY_NAME = "propertyName";

  final SyntaxNode node;
  final String text;
  final List<SyntaxNode> ancestors;
  final Optional<SyntaxNode> cls;
  final Optional<SyntaxNode> element;
  final Optional<SyntaxNode> member;

  DecoratedSite(final SyntaxWalk.Visit visit) {
    this.node = visit.node();
    this.text = SyntaxWalk.text(node);
    this.ancestors = visit.ancestors();
    this.cls = nearest(CLASS, Optional.empty());
    this.element = nearest(CLASS_ELEMENT, Optional.empty());
    this.member = nearest(MEMBER, Optional.of(node));
  }

  private Optional<SyntaxNode> nearest(final String kind, final Optional<SyntaxNode> self) {
    if (self.isPresent() && kind.equals(self.get().kind())) {
      return self;
    }
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if (kind.equals(ancestors.get(index).kind())) {
        return Optional.of(ancestors.get(index));
      }
    }
    return Optional.empty();
  }

  /** Returns the name of the member that holds the node, or empty when it has none. */
  String memberName() {
    return element.flatMap(DecoratedSite::declarationOf).map(DecoratedSite::nameOf).orElse("");
  }

  private static Optional<SyntaxNode> declarationOf(final SyntaxNode classElement) {
    return classElement.children().stream()
        .filter(child -> MEMBER.equals(child.kind()))
        .findFirst();
  }

  private static String nameOf(final SyntaxNode declaration) {
    return declaration.children().stream()
        .filter(child -> PROPERTY_NAME.equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }
}

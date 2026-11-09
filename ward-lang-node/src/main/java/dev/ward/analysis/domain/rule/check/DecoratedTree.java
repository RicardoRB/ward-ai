package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Tree queries shared by the decorated-class conditions. */
final class DecoratedTree {

  private static final String DECORATOR_LIST = "decoratorList";
  private static final String DECORATOR = "decorator";
  private static final String HERITAGE = "classHeritage";
  private static final String IDENTIFIER = "Identifier";

  private DecoratedTree() {}

  /** Tells whether a decorator directly on {@code owner} matches the pattern. */
  static boolean anyDecorator(final Optional<SyntaxNode> owner, final Pattern pattern) {
    return owner.isPresent()
        && owner.get().children().stream()
            .filter(child -> DECORATOR_LIST.equals(child.kind()))
            .flatMap(list -> list.children().stream())
            .filter(child -> DECORATOR.equals(child.kind()))
            .anyMatch(decorator -> pattern.matcher(SyntaxWalk.text(decorator)).find());
  }

  /** Tells whether {@code node} has a direct child of one of the kinds. */
  static boolean hasChildOf(final Optional<SyntaxNode> node, final Set<String> kinds) {
    return node.isPresent()
        && node.get().children().stream().anyMatch(child -> kinds.contains(child.kind()));
  }

  /** Tells whether the heritage of the class names the interface as an identifier. */
  static boolean implementsName(final Optional<SyntaxNode> cls, final String name) {
    return cls.isPresent()
        && cls.get().children().stream()
            .filter(child -> HERITAGE.equals(child.kind()))
            .flatMap(heritage -> SyntaxWalk.leaves(heritage).stream())
            .anyMatch(
                leaf -> IDENTIFIER.equals(leaf.kind()) && name.equals(leaf.fields().get("text")));
  }

  /** Tells whether the ancestors place the node inside a decorator of its class. */
  static boolean inClassDecorator(final List<SyntaxNode> ancestors) {
    for (int index = 2; index < ancestors.size(); index++) {
      if (DECORATOR.equals(ancestors.get(index).kind())
          && DECORATOR_LIST.equals(ancestors.get(index - 1).kind())
          && DecoratedSite.CLASS.equals(ancestors.get(index - 2).kind())) {
        return true;
      }
    }
    return false;
  }

  /** Converts a kebab-case name to camel case, so {@code aria-label} becomes {@code ariaLabel}. */
  static String camelCase(final String kebab) {
    final StringBuilder out = new StringBuilder();
    for (final String part : kebab.split("-")) {
      out.append(out.isEmpty() ? part : capitalize(part));
    }
    return out.toString();
  }

  private static String capitalize(final String part) {
    return part.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1);
  }
}

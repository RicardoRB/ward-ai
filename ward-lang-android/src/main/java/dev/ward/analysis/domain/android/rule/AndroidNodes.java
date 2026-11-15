package dev.ward.analysis.domain.android.rule;

import dev.ward.analysis.domain.android.AndroidSyntax;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Reads the tree of an Android XML file for the checks. The {@code android}, {@code app} and {@code
 * tools} prefixes are taken as written, not resolved through {@code xmlns}: shortcut, a file that
 * binds the Android namespace to another prefix is not read; upgrade when a real project does.
 */
final class AndroidNodes {

  static final String ANDROID = "android";

  private AndroidNodes() {}

  /** Returns every element of the tree in document order, the root first. */
  static List<SyntaxNode> elements(final SyntaxNode root) {
    final List<SyntaxNode> found = new ArrayList<>();
    final Deque<SyntaxNode> pending = new ArrayDeque<>();
    pending.push(root);
    while (!pending.isEmpty()) {
      final SyntaxNode node = pending.pop();
      if (AndroidSyntax.isElement(node.kind())) {
        found.add(node);
        for (int index = node.children().size() - 1; index >= 0; index--) {
          pending.push(node.children().get(index));
        }
      }
    }
    return found;
  }

  /** Returns the name of an element, or an empty text for a node that is not one. */
  static String name(final SyntaxNode element) {
    return element.fields().getOrDefault(AndroidSyntax.NAME, "");
  }

  /** Returns the attribute {@code prefix:local} of an element; {@code prefix} may be empty. */
  static Optional<SyntaxNode> attribute(
      final SyntaxNode element, final String prefix, final String local) {
    return element.children().stream()
        .filter(child -> AndroidSyntax.ATTRIBUTE.equals(child.kind()))
        .filter(child -> prefix.equals(child.fields().get(AndroidSyntax.PREFIX)))
        .filter(child -> local.equals(child.fields().get(AndroidSyntax.LOCAL)))
        .findFirst();
  }

  /** Returns the value of an attribute node. */
  static String value(final SyntaxNode attribute) {
    return attribute.fields().getOrDefault(AndroidSyntax.VALUE, "");
  }

  /**
   * Tells whether an element has the attribute {@code android:local} with the value {@code true}.
   */
  static boolean androidTrue(final SyntaxNode element, final String local) {
    return attribute(element, ANDROID, local)
        .map(AndroidNodes::value)
        .filter("true"::equals)
        .isPresent();
  }

  /** Tells whether an element has a child element named {@code name}. */
  static boolean hasChild(final SyntaxNode element, final String name) {
    return element.children().stream()
        .anyMatch(child -> AndroidSyntax.isElement(child.kind()) && name.equals(name(child)));
  }
}

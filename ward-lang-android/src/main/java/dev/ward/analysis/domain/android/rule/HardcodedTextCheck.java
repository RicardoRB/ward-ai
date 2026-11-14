package dev.ward.analysis.domain.android.rule;

import dev.ward.analysis.domain.android.AndroidSyntax;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.List;
import java.util.Set;

/**
 * Finds user-visible text written into a layout, menu, navigation or preference file instead of a
 * string resource: a non-empty literal in {@code android:text}, {@code hint}, {@code title}, {@code
 * summary} or {@code label}. A value that starts with {@code @} or {@code ?} refers to a resource
 * or a theme attribute, and one that starts with {@code @{} is a binding; none is literal. Files
 * that hold no layout text, such as the manifest and {@code values} files, are not read.
 */
public final class HardcodedTextCheck implements RuleCheck {

  private static final Set<String> ATTRIBUTES = Set.of("text", "hint", "title", "summary", "label");
  private static final Set<String> OTHER_ROOTS =
      Set.of(
          "manifest",
          "resources",
          "selector",
          "shape",
          "vector",
          "layer-list",
          "network-security-config",
          "adaptive-icon",
          "ripple");
  private static final String MESSAGE =
      "Hard-coded text: move it to a string resource and refer to it with @string/.";

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    if (OTHER_ROOTS.contains(AndroidNodes.name(root))) {
      return List.of();
    }
    return AndroidNodes.elements(root).stream()
        .flatMap(element -> element.children().stream())
        .filter(child -> AndroidSyntax.ATTRIBUTE.equals(child.kind()))
        .filter(child -> AndroidNodes.ANDROID.equals(child.fields().get(AndroidSyntax.PREFIX)))
        .filter(child -> ATTRIBUTES.contains(child.fields().get(AndroidSyntax.LOCAL)))
        .filter(child -> literal(AndroidNodes.value(child)))
        .map(child -> new RuleMatch(child.span(), MESSAGE))
        .toList();
  }

  private static boolean literal(final String value) {
    return !value.isBlank() && !value.startsWith("@") && !value.startsWith("?");
  }
}

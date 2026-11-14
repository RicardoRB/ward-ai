package dev.ward.analysis.domain.android.rule;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.List;
import java.util.Set;

/**
 * Finds an image view that a screen reader cannot describe: an {@code ImageView}, {@code
 * ImageButton} or one of their AppCompat and Material forms with neither {@code
 * android:contentDescription} nor {@code android:importantForAccessibility} of {@code no} or {@code
 * noHideDescendants}.
 */
public final class ContentDescriptionCheck implements RuleCheck {

  private static final Set<String> VIEWS =
      Set.of(
          "ImageView",
          "ImageButton",
          "androidx.appcompat.widget.AppCompatImageView",
          "androidx.appcompat.widget.AppCompatImageButton",
          "com.google.android.material.imageview.ShapeableImageView");
  private static final Set<String> HIDDEN = Set.of("no", "noHideDescendants");
  private static final String MESSAGE =
      "Image view has no content description: add android:contentDescription, or mark it with"
          + " android:importantForAccessibility=\"no\" when it is decorative.";

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return AndroidNodes.elements(root).stream()
        .filter(element -> VIEWS.contains(AndroidNodes.name(element)))
        .filter(element -> !described(element))
        .map(element -> new RuleMatch(element.span(), MESSAGE))
        .toList();
  }

  private static boolean described(final SyntaxNode element) {
    return AndroidNodes.attribute(element, AndroidNodes.ANDROID, "contentDescription").isPresent()
        || AndroidNodes.attribute(element, AndroidNodes.ANDROID, "importantForAccessibility")
            .map(AndroidNodes::value)
            .filter(HIDDEN::contains)
            .isPresent();
  }
}

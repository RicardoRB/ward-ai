package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The text a screen reader announces for a JSX element, as Biome 2.5.15's {@code
 * get_accessible_child_text} reads it
 * (crates/biome_js_analyze/src/lint/a11y/no_ambiguous_anchor_text.rs). An element's own aria-label,
 * or the alt of an img, replaces its children's text; a hidden element contributes nothing.
 */
final class JsxAccessibleText {

  private static final String ELEMENT = "jsxElement";
  private static final String TEXT = "jsxText";
  private static final String OPENING = JsxAttributes.OPENING;
  private static final String SELF_CLOSING = JsxAttributes.SELF_CLOSING;
  private static final String ARIA_LABEL = "aria-label";
  private static final String ARIA_HIDDEN = "aria-hidden";
  private static final String ALT = "alt";
  private static final String IMG = "img";
  private static final String INPUT = "input";
  private static final String TYPE = "type";
  private static final String HIDDEN = "hidden";
  private static final String SPACE = " ";
  private static final Pattern IGNORED_PUNCTUATION = Pattern.compile("[,.?¿!‽¡;:]");

  private JsxAccessibleText() {}

  /** The standardized accessible text of a jsxElement and its children, as Biome computes it. */
  static String ofElement(final SyntaxNode element, final String source) {
    final Optional<String> own =
        element.children().stream()
            .filter(child -> OPENING.equals(child.kind()))
            .findFirst()
            .flatMap(opening -> ofTag(opening, source));
    if (own.isPresent()) {
      return own.get();
    }
    final String children =
        element.children().stream()
            .map(child -> ofChild(child, source))
            .collect(Collectors.joining(SPACE));
    return standardize(children);
  }

  private static String ofChild(final SyntaxNode child, final String source) {
    return switch (child.kind()) {
      case TEXT -> JsxTruthiness.raw(child, source);
      case ELEMENT -> ofElement(child, source);
      case SELF_CLOSING -> ofTag(child, source).orElse("");
      default -> "";
    };
  }

  /** The text an opening or self-closing tag gives itself: hidden, aria-label or img alt. */
  private static Optional<String> ofTag(final SyntaxNode tag, final String source) {
    if (hidden(tag, source)) {
      return Optional.of("");
    }
    final Optional<String> label =
        JsxAttributes.of(tag).stream()
            .filter(attribute -> ARIA_LABEL.equals(JsxAttributes.name(attribute)))
            .findFirst()
            .flatMap(attribute -> JsxAttributes.rawLiteral(attribute, source));
    if (label.isPresent()) {
      return label.map(JsxAccessibleText::standardize);
    }
    if (!IMG.equals(JsxAttributes.elementName(tag))) {
      return Optional.empty();
    }
    return JsxAttributes.of(tag).stream()
        .filter(attribute -> ALT.equals(JsxAttributes.name(attribute)))
        .findFirst()
        .flatMap(attribute -> JsxAttributes.rawLiteral(attribute, source))
        .map(JsxAccessibleText::standardize);
  }

  private static boolean hidden(final SyntaxNode tag, final String source) {
    final boolean ariaHidden = JsxTruthiness.truthyAttribute(tag, ARIA_HIDDEN, source).isPresent();
    return ariaHidden || INPUT.equals(JsxAttributes.elementName(tag)) && hiddenInput(tag, source);
  }

  private static boolean hiddenInput(final SyntaxNode input, final String source) {
    return JsxAttributes.of(input).stream()
        .filter(attribute -> TYPE.equals(JsxAttributes.name(attribute)))
        .findFirst()
        .flatMap(attribute -> JsxAttributes.rawLiteral(attribute, source))
        .filter(HIDDEN::equals)
        .isPresent();
  }

  /** Lower-cases the text, drops the punctuation Biome drops, and collapses the spaces. */
  static String standardize(final String text) {
    final String plain = IGNORED_PUNCTUATION.matcher(text).replaceAll("");
    return String.join(SPACE, plain.toLowerCase(Locale.ROOT).trim().split("\\s+")).trim();
  }
}

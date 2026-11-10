package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a video or audio element with no track of kind captions, at the element, as Biome
 * 2.5.15's {@code useMediaCaption} does. A muted element or one with a spread is silent; a
 * self-closing media element has no track and is always reported.
 */
public final class BiomeMediaCaptionCheck implements RuleCheck {

  private static final Set<String> MEDIA = Set.of("video", "audio");
  private static final String TRACK = "track";
  private static final String KIND = "kind";
  private static final String CAPTIONS = "captions";
  private static final String OPENING = JsxAttributes.OPENING;
  private static final String CLOSING = "jsxClosingElement";
  private static final String ELEMENT = "jsxElement";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeMediaCaptionCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(ELEMENT, JsxAttributes.SELF_CLOSING)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(BiomeMediaCaptionCheck::reported)
        .map(node -> CheckSupport.at(node.span(), message))
        .toList();
  }

  /** Whether the element is media without muted or a spread, and no captions track. */
  private static boolean reported(final SyntaxNode node) {
    if (JsxAttributes.SELF_CLOSING.equals(node.kind())) {
      return mediaWithoutMutedOrSpread(node);
    }
    final Optional<SyntaxNode> opening =
        node.children().stream().filter(child -> OPENING.equals(child.kind())).findFirst();
    return opening.isPresent()
        && mediaWithoutMutedOrSpread(opening.get())
        && node.children().stream()
            .noneMatch(
                child ->
                    !OPENING.equals(child.kind())
                        && !CLOSING.equals(child.kind())
                        && isCaptionsTrack(child));
  }

  private static boolean mediaWithoutMutedOrSpread(final SyntaxNode element) {
    return MEDIA.contains(JsxAttributes.elementName(element))
        && JsxAttributes.of(element).stream()
            .noneMatch(attribute -> "muted".equals(JsxAttributes.name(attribute)))
        && !JsxAttributes.hasSpread(element);
  }

  /**
   * A track child, opening or self-closing, whose kind is captions in any letter case. The grammar
   * wraps a self-closing child in a jsxElement, so the tag is the opening or self-closing child.
   */
  private static boolean isCaptionsTrack(final SyntaxNode child) {
    final SyntaxNode element =
        child.children().stream()
            .filter(c -> OPENING.equals(c.kind()) || JsxAttributes.SELF_CLOSING.equals(c.kind()))
            .findFirst()
            .orElse(child);
    return TRACK.equals(JsxAttributes.elementName(element))
        && JsxAttributes.of(element).stream()
            .filter(attribute -> KIND.equals(JsxAttributes.name(attribute)))
            .anyMatch(
                attribute ->
                    JsxAttributes.literal(attribute)
                        .filter(CAPTIONS::equalsIgnoreCase)
                        .isPresent());
  }
}

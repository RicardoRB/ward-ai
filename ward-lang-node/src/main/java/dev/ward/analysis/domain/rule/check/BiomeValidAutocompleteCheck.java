package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an input whose static autocomplete attribute holds a value Biome 2.5.15 does not accept,
 * at the attribute, as its {@code useValidAutocomplete} does. The tokens are checked as Biome
 * checks them: none alone, an empty value, a section- prefix, a billing or shipping address pair,
 * or tokens that are all valid. A dynamic or bare value is not read. shortcut: the input-components
 * option is fixed to input, upgrade when the rule declares that option.
 */
public final class BiomeValidAutocompleteCheck implements RuleCheck {

  private static final String INPUT = "input";
  private static final String AUTOCOMPLETE = "autocomplete";
  private static final String AUTOCOMPLETE_CAMEL = "autoComplete";
  private static final String NONE = "none";
  private static final String SECTION_PREFIX = "section-";
  private static final Set<String> BILLING_SHIPPING = Set.of("billing", "shipping");
  private static final Set<String> VALID_VALUES =
      Set.of(
          "additional-name",
          "address-level1",
          "address-level2",
          "address-level3",
          "address-level4",
          "address-line1",
          "address-line2",
          "address-line3",
          "bday",
          "bday-day",
          "bday-month",
          "bday-year",
          "cc-additional-name",
          "cc-csc",
          "cc-exp",
          "cc-exp-month",
          "cc-exp-year",
          "cc-family-name",
          "cc-given-name",
          "cc-name",
          "cc-number",
          "cc-type",
          "country",
          "country-name",
          "current-password",
          "email",
          "family-name",
          "given-name",
          "honorific-prefix",
          "honorific-suffix",
          "impp",
          "language",
          "name",
          "new-password",
          "nickname",
          "off",
          "on",
          "one-time-code",
          "organization",
          "organization-title",
          "photo",
          "postal-code",
          "sex",
          "street-address",
          "tel",
          "tel-area-code",
          "tel-country-code",
          "tel-extension",
          "tel-local",
          "tel-national",
          "transaction-amount",
          "transaction-currency",
          "url",
          "username",
          "webauthn");
  private static final Set<String> BILLING_AND_SHIPPING_ADDRESS =
      Set.of(
          "address-level1",
          "address-level2",
          "address-level3",
          "address-level4",
          "address-line1",
          "address-line2",
          "address-line3",
          "country",
          "country-name",
          "postal-code",
          "street-address");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeValidAutocompleteCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the static value is read from the source text. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the autocomplete check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .map(SyntaxWalk.Visit::node)
        .filter(element -> INPUT.equals(JsxAttributes.elementName(element)))
        .flatMap(element -> autocomplete(element).stream())
        .filter(attribute -> invalid(attribute, unit.text()))
        .map(attribute -> CheckSupport.at(attribute.span(), message))
        .toList();
  }

  /** The autocomplete attribute, or autoComplete when there is no autocomplete. */
  private static Optional<SyntaxNode> autocomplete(final SyntaxNode element) {
    final Optional<SyntaxNode> lower = attributeNamed(element, AUTOCOMPLETE);
    return lower.isPresent() ? lower : attributeNamed(element, AUTOCOMPLETE_CAMEL);
  }

  private static Optional<SyntaxNode> attributeNamed(final SyntaxNode element, final String name) {
    return JsxAttributes.of(element).stream()
        .filter(attribute -> name.equals(JsxAttributes.name(attribute)))
        .findFirst();
  }

  private static boolean invalid(final SyntaxNode attribute, final String source) {
    return staticText(attribute, source)
        .map(BiomeValidAutocompleteCheck::tokens)
        .filter(tokens -> !(tokens.length == 1 && NONE.equals(tokens[0])) && !validTokens(tokens))
        .isPresent();
  }

  /** The text of a JSX string or of a string literal in braces; empty for any other value. */
  private static Optional<String> staticText(final SyntaxNode attribute, final String source) {
    return JsxAttributes.value(attribute)
        .map(value -> JsxTruthiness.raw(value, source))
        .flatMap(
            raw -> {
              final String inner =
                  raw.startsWith("{") && raw.endsWith("}")
                      ? raw.substring(1, raw.length() - 1).trim()
                      : raw;
              return JsxTruthiness.isQuoted(inner)
                  ? Optional.of(inner.substring(1, inner.length() - 1))
                  : Optional.empty();
            });
  }

  /** The whitespace-separated tokens, as Biome's split_ascii_whitespace reads them. */
  private static String[] tokens(final String text) {
    final String trimmed = text.trim();
    return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
  }

  private static boolean validTokens(final String... tokens) {
    return switch (tokens.length) {
      case 0 -> true;
      case 1 -> singleValid(tokens[0]);
      default -> multiValid(tokens);
    };
  }

  private static boolean singleValid(final String token) {
    return token.isEmpty() || token.startsWith(SECTION_PREFIX) || VALID_VALUES.contains(token);
  }

  private static boolean multiValid(final String... tokens) {
    final String first = tokens[0];
    final String second = tokens[1];
    return first.startsWith(SECTION_PREFIX)
        || BILLING_SHIPPING.contains(first)
            && (BILLING_AND_SHIPPING_ADDRESS.contains(second) || VALID_VALUES.contains(second))
        || Arrays.stream(tokens).allMatch(VALID_VALUES::contains);
  }
}

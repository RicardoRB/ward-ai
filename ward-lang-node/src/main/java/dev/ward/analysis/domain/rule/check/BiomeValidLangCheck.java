package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a {@code lang} attribute on {@code html} whose static value is not a valid language tag,
 * at the value, as Biome 2.5.15's {@code useValidLang} does. A tag is a language, a language with a
 * script or a country, or a language with a script and a country; a dynamic value is not read.
 */
public final class BiomeValidLangCheck implements RuleCheck {

  private static final String HTML = "html";
  private static final String LANG = "lang";
  private static final String SEPARATOR = "-";
  private static final int LANGUAGE_ONLY = 1;
  private static final int LANGUAGE_AND_ONE = 2;
  private static final int LANGUAGE_SCRIPT_COUNTRY = 3;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeValidLangCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the static value is read from the source text. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the valid-lang check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .map(SyntaxWalk.Visit::node)
        .filter(element -> HTML.equals(JsxAttributes.elementName(element)))
        .flatMap(element -> langValue(element).stream())
        .filter(value -> invalid(staticText(JsxTruthiness.raw(value, unit.text()))))
        .map(value -> CheckSupport.at(value.span(), message))
        .toList();
  }

  /** The first lang attribute's value, when it has one. */
  private static Optional<SyntaxNode> langValue(final SyntaxNode element) {
    return JsxAttributes.of(element).stream()
        .filter(attribute -> LANG.equals(JsxAttributes.name(attribute)))
        .findFirst()
        .flatMap(JsxAttributes::value);
  }

  /** The text of a JSX string, or of a string literal in braces; empty for any other value. */
  private static String staticText(final String raw) {
    if (raw.startsWith("{") && raw.endsWith("}")) {
      final String inner = raw.substring(1, raw.length() - 1).trim();
      return JsxTruthiness.isQuoted(inner) ? inner.substring(1, inner.length() - 1) : null;
    }
    return JsxTruthiness.isQuoted(raw) ? raw.substring(1, raw.length() - 1) : null;
  }

  /** Whether the tag is not one Biome accepts, split on the dashes as Biome splits it. */
  private static boolean invalid(final String tag) {
    if (tag == null) {
      return false;
    }
    final String[] parts = tag.split(SEPARATOR, -1);
    return switch (parts.length) {
      case LANGUAGE_ONLY -> !IsoSubtags.isLanguage(parts[0]);
      case LANGUAGE_AND_ONE ->
          !IsoSubtags.isLanguage(parts[0])
              || !(IsoSubtags.isScript(parts[1]) || IsoSubtags.isCountry(parts[1]));
      case LANGUAGE_SCRIPT_COUNTRY ->
          !(IsoSubtags.isLanguage(parts[0])
              && IsoSubtags.isScript(parts[1])
              && IsoSubtags.isCountry(parts[2]));
      default -> true;
    };
  }
}

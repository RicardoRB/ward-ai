package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a function whose KDoc lacks an {@code @param} tag for a parameter, or an {@code @return}
 * tag when the function declares a result other than Unit, as Checkstyle's JavadocMethod checks the
 * tags of a method. Functions without a KDoc comment are left to the missing-doc rules.
 */
public final class KotlinKdocMethodCheck implements UnitCheck {

  private static final String FUNCTION = "functionDeclaration";
  private static final String PARAMETERS = "functionValueParameters";
  private static final String PARAMETER = "functionValueParameter";
  private static final String IDENTIFIER = "Identifier";
  private static final String TYPE = "type";
  private static final String COLON = ":";
  private static final String UNIT = "Unit";
  private static final String RETURN_TAG = "@return";
  private static final String PARAM_TAG = "@param\\s+%s\\b";
  private static final String VALUE = "{value}";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the missing tag
   */
  public KotlinKdocMethodCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final KotlinDocs docs = new KotlinDocs(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(FUNCTION))) {
      final SyntaxNode function = visit.node();
      final Optional<SyntaxNode> kdoc = docs.documenting(function);
      if (kdoc.isPresent()) {
        final String text = KotlinDocs.text(kdoc.get());
        for (final String tag : missingTags(function, text)) {
          matches.add(CheckSupport.at(kdoc.get().span(), message.replace(VALUE, tag)));
        }
      }
    }
    return List.copyOf(matches);
  }

  private static List<String> missingTags(final SyntaxNode function, final String text) {
    final List<String> missing = new ArrayList<>();
    for (final String name : parameterNames(function)) {
      if (!Pattern.compile(PARAM_TAG.formatted(Pattern.quote(name))).matcher(text).find()) {
        missing.add("@param " + name);
      }
    }
    if (returnsValue(function) && !text.contains(RETURN_TAG)) {
      missing.add(RETURN_TAG);
    }
    return missing;
  }

  private static List<String> parameterNames(final SyntaxNode function) {
    final List<String> names = new ArrayList<>();
    for (final SyntaxNode list : KotlinDocs.childrenOf(function, PARAMETERS)) {
      for (final SyntaxNode parameter : KotlinDocs.childrenOf(list, PARAMETER)) {
        SyntaxWalk.leaves(parameter).stream()
            .filter(leaf -> IDENTIFIER.equals(leaf.kind()))
            .findFirst()
            .ifPresent(leaf -> names.add(KotlinDocs.text(leaf)));
      }
    }
    return names;
  }

  private static boolean returnsValue(final SyntaxNode function) {
    final boolean hasColon =
        function.children().stream().anyMatch(child -> COLON.equals(KotlinDocs.text(child)));
    return hasColon
        && KotlinDocs.childrenOf(function, TYPE).stream()
            .map(SyntaxWalk::text)
            .anyMatch(type -> !UNIT.equals(type));
  }
}

package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each function whose documentation comment lacks a tag for one of its parameters, for its
 * return value or for its thrown errors, like Checkstyle's {@code JavadocMethod} for a documented
 * method. Swift writes these tags as {@code - Parameter name:}, {@code - Returns:} and {@code -
 * Throws:}. A function without a documentation comment is left to {@code missing-javadoc-method}.
 */
public final class JavadocMethodCheck implements RuleCheck {

  private static final String FUNCTION = "function_declaration";
  private static final String SIGNATURE = "function_signature";
  private static final String RESULT = "function_result";
  private static final String THROWS = "throws_clause";
  private static final String PARAMETER = "parameter";
  private static final String LOCAL_NAME = "local_parameter_name";
  private static final String TAG = "{value}";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} is the missing tag
   */
  public JavadocMethodCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(FUNCTION))) {
      final Optional<SyntaxNode> comment = Declaration.of(visit, Set.of()).previousSibling();
      if (comment.isPresent() && SyntaxWalk.COMMENT_KIND.equals(comment.get().kind())) {
        missingTags(visit.node(), commentText(comment.get()))
            .forEach(tag -> matches.add(match(visit.node(), tag)));
      }
    }
    return List.copyOf(matches);
  }

  private static String commentText(final SyntaxNode comment) {
    return comment.fields().getOrDefault("text", SyntaxWalk.text(comment));
  }

  private RuleMatch match(final SyntaxNode function, final String tag) {
    return new RuleMatch(function.span(), message.replace(TAG, tag));
  }

  private static List<String> missingTags(final SyntaxNode function, final String comment) {
    final List<String> missing = new ArrayList<>();
    function.children().stream()
        .filter(child -> SIGNATURE.equals(child.kind()))
        .forEach(signature -> missing.addAll(missingInSignature(signature, comment)));
    return missing;
  }

  private static List<String> missingInSignature(final SyntaxNode signature, final String comment) {
    final List<String> missing = new ArrayList<>();
    parameterNames(signature).stream()
        .filter(name -> !hasTag(comment, "Parameter\\s*" + Pattern.quote(name)))
        .forEach(name -> missing.add("- Parameter " + name + ":"));
    missing.addAll(missingTag(signature, comment, RESULT, "Returns"));
    missing.addAll(missingTag(signature, comment, THROWS, "Throws"));
    return missing;
  }

  private static List<String> missingTag(
      final SyntaxNode signature, final String comment, final String kind, final String tag) {
    final boolean missing = hasNode(signature, kind) && !hasTag(comment, tag);
    return missing ? List.of("- " + tag + ":") : List.of();
  }

  private static List<String> parameterNames(final SyntaxNode signature) {
    final List<String> names = new ArrayList<>();
    for (final SyntaxWalk.Visit parameter : SyntaxWalk.visits(signature, Set.of(PARAMETER))) {
      parameter.node().children().stream()
          .filter(child -> LOCAL_NAME.equals(child.kind()))
          .forEach(child -> names.add(SyntaxWalk.text(child)));
    }
    return names;
  }

  private static boolean hasNode(final SyntaxNode signature, final String kind) {
    return !SyntaxWalk.visits(signature, Set.of(kind)).isEmpty();
  }

  private static boolean hasTag(final String comment, final String tag) {
    return Pattern.compile("-\\s*" + tag + "\\s*:").matcher(comment).find();
  }
}

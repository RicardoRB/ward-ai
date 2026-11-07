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
  private static final String MESSAGE = "documentation has no {value} tag";
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
    for (final SyntaxNode signature : function.children()) {
      if ("function_signature".equals(signature.kind())) {
        for (final String name : parameterNames(signature)) {
          if (!hasTag(comment, "Parameter\\s*" + Pattern.quote(name))) {
            missing.add("- Parameter " + name + ":");
          }
        }
        if (hasNode(signature, "function_result") && !hasTag(comment, "Returns")) {
          missing.add("- Returns:");
        }
        if (hasNode(signature, "throws_clause") && !hasTag(comment, "Throws")) {
          missing.add("- Throws:");
        }
      }
    }
    return missing;
  }

  private static List<String> parameterNames(final SyntaxNode signature) {
    final List<String> names = new ArrayList<>();
    for (final SyntaxWalk.Visit parameter : SyntaxWalk.visits(signature, Set.of("parameter"))) {
      for (final SyntaxNode child : parameter.node().children()) {
        if ("local_parameter_name".equals(child.kind())) {
          names.add(SyntaxWalk.text(child));
        }
      }
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

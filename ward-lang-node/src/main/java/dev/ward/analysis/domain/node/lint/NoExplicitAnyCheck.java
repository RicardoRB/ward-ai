package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Reports the {@code any} keyword of a type, but not in a rest parameter array when allowed. */
public final class NoExplicitAnyCheck implements RuleCheck {

  private static final String ANY = "any";
  private static final String PREDEFINED = "predefinedType";
  private static final String REST = "restParameter";
  private static final String ANNOTATION = "typeAnnotation";
  private static final Pattern ARRAY = Pattern.compile("(Readonly)?Array<.*>");

  private final boolean ignoreRestArgs;
  private final String message;

  /** Creates the check with the rule's option and message. */
  public NoExplicitAnyCheck(final boolean ignoreRestArgs, final String message) {
    this.ignoreRestArgs = ignoreRestArgs;
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<Span> allowed = ignoreRestArgs ? restAnnotations(root) : List.of();
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode node : SyntaxText.nodesOf(root, PREDEFINED)) {
      if (ANY.equals(SyntaxText.of(node)) && !isWithin(node.span(), allowed)) {
        out.add(new RuleMatch(node.span(), message));
      }
    }
    return out;
  }

  /** Returns the spans of the array type annotations of the rest parameters. */
  private static List<Span> restAnnotations(final SyntaxNode root) {
    final List<Span> spans = new ArrayList<>();
    for (final SyntaxNode rest : SyntaxText.nodesOf(root, REST)) {
      SyntaxText.childrenOf(rest, ANNOTATION).stream()
          .filter(NoExplicitAnyCheck::isArrayAnnotation)
          .forEach(child -> spans.add(child.span()));
    }
    return spans;
  }

  /** Reports whether a type annotation is an array type, {@code T[]} or {@code Array<T>}. */
  private static boolean isArrayAnnotation(final SyntaxNode annotation) {
    final String type = SyntaxText.of(annotation).replaceFirst("^:\\s*", "");
    return type.endsWith("[]") || ARRAY.matcher(type).matches();
  }

  private static boolean isWithin(final Span inner, final List<Span> outers) {
    return outers.stream()
        .anyMatch(
            outer ->
                outer.startOffset() <= inner.startOffset()
                    && inner.endOffset() <= outer.endOffset());
  }
}

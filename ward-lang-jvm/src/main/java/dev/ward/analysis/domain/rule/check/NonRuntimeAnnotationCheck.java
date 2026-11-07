package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a reflective lookup of an annotation that is declared in the same file without
 * {@code @Retention(RUNTIME)}: the annotation is not there at run time, so the lookup finds
 * nothing. An annotation declared elsewhere is not known, so it is not checked.
 */
public final class NonRuntimeAnnotationCheck implements RuleCheck {

  private static final String ANNOTATION_TYPE = "annotationTypeDeclaration";
  private static final String IDENTIFIER = "identifier";
  private static final String EXPRESSION = "expression";
  private static final String RETENTION = "Retention";
  private static final Pattern RUNTIME = Pattern.compile("RUNTIME\\)?$");
  private static final Pattern LOOKUP =
      Pattern.compile(
          "\\.(?:isAnnotationPresent|getAnnotation|getDeclaredAnnotation|getAnnotationsByType|"
              + "getDeclaredAnnotationsByType)\\((?:[\\w.]*\\.)?(\\w+)\\.class\\)$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the lookup
   */
  public NonRuntimeAnnotationCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<String> hidden = nonRuntime(root);
    if (hidden.isEmpty()) {
      return List.of();
    }
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION))) {
      final Matcher lookup = LOOKUP.matcher(SyntaxWalk.text(visit.node()));
      if (lookup.find() && hidden.contains(lookup.group(1))) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static Set<String> nonRuntime(final SyntaxNode root) {
    final Set<String> names = new java.util.HashSet<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(ANNOTATION_TYPE))) {
      final boolean runtime =
          visit.ancestors().stream()
              .reduce((first, second) -> second)
              .map(AnnotationTexts::of)
              .orElse(List.of())
              .stream()
              .anyMatch(
                  text ->
                      RETENTION.equals(AnnotationTexts.simpleName(text))
                          && RUNTIME.matcher(text).find());
      if (!runtime) {
        visit.node().children().stream()
            .filter(child -> IDENTIFIER.equals(child.kind()))
            .findFirst()
            .ifPresent(id -> names.add(SyntaxWalk.text(id)));
      }
    }
    return names;
  }
}

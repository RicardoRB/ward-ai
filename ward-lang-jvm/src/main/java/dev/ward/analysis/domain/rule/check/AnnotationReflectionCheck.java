package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a reflective query for an annotation that is not kept at run time: {@code
 * isAnnotationPresent(A.class)}, {@code getAnnotation(A.class)} and the like, where {@code A} is an
 * annotation type declared in the file without {@code @Retention(RUNTIME)}, or {@code Override} or
 * {@code SuppressWarnings}. The query always answers no.
 */
public final class AnnotationReflectionCheck implements UnitCheck {
  private static final String ANNOTATION_TYPE = "annotationTypeDeclaration";
  private static final String METHOD_CALL = "methodCall";
  private static final String IDENTIFIER = "identifier";
  private static final Set<String> SOURCE_ONLY = Set.of("Override", "SuppressWarnings");
  private static final int TYPE = 2;
  private static final Pattern QUERY =
      Pattern.compile(
          "^(isAnnotationPresent|getAnnotation|getDeclaredAnnotation|getAnnotationsByType"
              + "|getDeclaredAnnotationsByType)\\((?:[\\w.]*\\.)?(\\w+)\\.class[,)]");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the query
   */
  public AnnotationReflectionCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final Set<String> hidden = new HashSet<>(SOURCE_ONLY);
    SyntaxWalk.descendants(root).forEach(node -> hiddenType(node).ifPresent(hidden::add));
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(METHOD_CALL))) {
      final Matcher query = QUERY.matcher(SyntaxWalk.text(visit.node()));
      if (query.find() && hidden.contains(query.group(TYPE))) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  /**
   * Returns the name of the annotation type that {@code holder} declares without runtime retention.
   */
  private static Optional<String> hiddenType(final SyntaxNode holder) {
    final Optional<SyntaxNode> declaration =
        holder.children().stream().filter(c -> ANNOTATION_TYPE.equals(c.kind())).findFirst();
    final String text = SyntaxWalk.text(holder);
    final boolean runtime = text.contains("Retention(") && text.contains("RUNTIME");
    if (declaration.isEmpty() || runtime) {
      return Optional.empty();
    }
    return declaration.get().children().stream()
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text);
  }
}

package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a declaration that has two qualifier annotations. The qualifiers are {@code @Named} and
 * the annotation types declared in the same file with {@code @Qualifier}; any other annotation is
 * not known to be one, so it is not counted.
 */
public final class QualifierCountCheck implements RuleCheck {

  private static final String ANNOTATION_TYPE = "annotationTypeDeclaration";
  private static final String IDENTIFIER = "identifier";
  private static final String NAMED = "Named";
  private static final String QUALIFIER = "Qualifier";
  private static final Set<String> DECLARATIONS =
      Set.of(
          "formalParameter", "localVariableDeclaration", "classBodyDeclaration", "typeDeclaration");
  private static final int TWO = 2;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the declaration
   */
  public QualifierCountCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<String> qualifiers = qualifiers(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, DECLARATIONS)) {
      final long count =
          AnnotationTexts.of(visit.node()).stream()
              .map(AnnotationTexts::simpleName)
              .filter(qualifiers::contains)
              .distinct()
              .count();
      if (count >= TWO) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static Set<String> qualifiers(final SyntaxNode root) {
    final Set<String> names = new java.util.HashSet<>(Set.of(NAMED));
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(ANNOTATION_TYPE))) {
      final boolean qualifier =
          visit.ancestors().stream()
              .filter(node -> DECLARATIONS.contains(node.kind()))
              .reduce((first, second) -> second)
              .filter(
                  wrapper ->
                      AnnotationTexts.of(wrapper).stream()
                          .anyMatch(text -> QUALIFIER.equals(AnnotationTexts.simpleName(text))))
              .isPresent();
      visit.node().children().stream()
          .filter(child -> IDENTIFIER.equals(child.kind()))
          .findFirst()
          .filter(id -> qualifier)
          .ifPresent(id -> names.add(SyntaxWalk.text(id)));
    }
    return names;
  }
}

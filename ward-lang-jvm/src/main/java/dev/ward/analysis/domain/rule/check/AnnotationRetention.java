package dev.ward.analysis.domain.rule.check;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads whether an annotation type declared in the file is kept at run time. */
final class AnnotationRetention {
  /** The nodes that hold the modifiers of a top-level type and of a member of a type. */
  private static final Set<String> HOLDERS =
      Set.of(
          "typeDeclaration",
          "classBodyDeclaration",
          "interfaceBodyDeclaration",
          "annotationTypeElementDeclaration"
      );
  private static final String RETENTION = "Retention";
  private static final Pattern RUNTIME = Pattern.compile("RUNTIME\\)?$");

  private AnnotationRetention() {}

  /**
   * Tells whether the annotation type carries {@code @Retention(RUNTIME)}, written with or without
   * {@code RetentionPolicy}. The annotation of a nested type is on the member that wraps it.
   *
   * @param annotationType the visit of an {@code annotationTypeDeclaration}
   * @return true when the type is kept at run time
   */
  static boolean runtime(final SyntaxWalk.Visit annotationType) {
    return annotationType.ancestors().stream()
        .filter(ancestor -> HOLDERS.contains(ancestor.kind()))
        .reduce((outer, inner) -> inner)
        .map(AnnotationTexts::of)
        .orElse(List.of())
        .stream()
        .anyMatch(
            text ->
                RETENTION.equals(AnnotationTexts.simpleName(text)) && RUNTIME.matcher(text).find()
        );
  }
}

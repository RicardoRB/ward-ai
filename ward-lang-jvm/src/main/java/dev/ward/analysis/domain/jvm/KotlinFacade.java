package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The facade class of a Kotlin file, which Java sees for its top-level functions and properties:
 * the literal value of {@code @file:JvmName}, else the file name with {@code Kt}.
 */
final class KotlinFacade {
  private static final String PREAMBLE = "preamble";
  private static final String FILE_ANNOTATIONS = "fileAnnotations";
  private static final String FILE_ANNOTATION = "fileAnnotation";
  private static final String UNESCAPED_ANNOTATION = "unescapedAnnotation";
  private static final String IDENTIFIER = "identifier";
  private static final String VALUE_ARGUMENTS = "valueArguments";
  private static final String JVM_NAME = "JvmName";
  private static final String FACADE_SUFFIX = "Kt";
  private static final String KOTLIN_SUFFIX = ".kt";
  private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]+)\"");

  private KotlinFacade() {}

  /**
   * Returns the simple name of the facade of the file at {@code path}, whose tree is {@code root}.
   */
  static String nameOf(final SyntaxNode root, final String path) {
    return jvmName(root).orElseGet(() -> fileFacadeName(path));
  }

  private static String fileFacadeName(final String path) {
    final String file = path.substring(path.lastIndexOf('/') + 1);
    final String base =
        file.endsWith(KOTLIN_SUFFIX)
            ? file.substring(0, file.length() - KOTLIN_SUFFIX.length())
            : file;
    return Character.toUpperCase(base.charAt(0)) + base.substring(1) + FACADE_SUFFIX;
  }

  private static Optional<String> jvmName(final SyntaxNode root) {
    return KotlinSyntax.children(root, PREAMBLE).stream()
        .flatMap(preamble -> KotlinSyntax.children(preamble, FILE_ANNOTATIONS).stream())
        .flatMap(annotations -> KotlinSyntax.children(annotations, FILE_ANNOTATION).stream())
        .flatMap(annotation -> KotlinSyntax.children(annotation, UNESCAPED_ANNOTATION).stream())
        .map(KotlinFacade::jvmNameArgument)
        .flatMap(Optional::stream)
        .findFirst();
  }

  /**
   * Returns the string argument of a {@code JvmName} annotation, or none for another annotation.
   */
  private static Optional<String> jvmNameArgument(final SyntaxNode annotation) {
    final boolean named =
        KotlinSyntax.children(annotation, IDENTIFIER).stream()
            .map(KotlinSyntax::segments)
            .anyMatch(JVM_NAME::equals);
    if (!named) {
      return Optional.empty();
    }
    return KotlinSyntax.children(annotation, VALUE_ARGUMENTS).stream()
        .map(JavaSyntax::dotted)
        .map(STRING_LITERAL::matcher)
        .filter(Matcher::find)
        .map(match -> match.group(1))
        .findFirst();
  }
}

package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the element values of the annotations among the modifiers of a declaration, by {@code
 * <annotation>#<element>}: a string without its quotes, a class literal as the qualified name of
 * the class, an enum constant or a name as its last segment, any other constant as written, and an
 * array as its elements. An unnamed value belongs to {@code value}.
 */
final class AnnotationSourceValues {
  private static final String ANNOTATION = "annotation";
  private static final String FIELD_VALUES = "annotationFieldValues";
  private static final String FIELD_VALUE = "annotationFieldValue";
  private static final String VALUE = "annotationValue";
  private static final String EXPRESSION = "expression";
  private static final String DEFAULT_ELEMENT = "value";
  private static final String CLASS_SUFFIX = ".class";
  private static final String QUOTE = "\"";

  private AnnotationSourceValues() {}

  /** Returns the element values of the annotations among {@code modifiers}. */
  static Map<String, List<String>> of(final List<SyntaxNode> modifiers, final TypeScope scope) {
    final Map<String, List<String>> values = new LinkedHashMap<>();
    for (final SyntaxNode modifier : modifiers) {
      Syntax.find(modifier, ANNOTATION).ifPresent(annotation -> read(annotation, scope, values));
    }
    return values;
  }

  private static void read(
      final SyntaxNode annotation, final TypeScope scope, final Map<String, List<String>> into) {
    final String name =
        Syntax.child(annotation, "qualifiedName")
            .map(written -> JavaModifiers.annotationName(Syntax.text(written), scope))
            .orElse("");
    Syntax.child(annotation, FIELD_VALUES).stream()
        .flatMap(list -> Syntax.children(list, FIELD_VALUE).stream())
        .forEach(
            pair -> {
              final String element =
                  Syntax.child(pair, "identifier").map(Syntax::text).orElse(DEFAULT_ELEMENT);
              final List<String> found = new ArrayList<>();
              Syntax.child(pair, VALUE).ifPresent(value -> collect(value, scope, found));
              into.computeIfAbsent(name + "#" + element, key -> new ArrayList<>()).addAll(found);
            });
  }

  private static void collect(
      final SyntaxNode value, final TypeScope scope, final List<String> into) {
    final List<SyntaxNode> nested = Syntax.children(value, VALUE);
    if (!nested.isEmpty() || Syntax.hasToken(value, "{")) {
      nested.forEach(inner -> collect(inner, scope, into));
      return;
    }
    Syntax.child(value, EXPRESSION).ifPresent(expression -> into.add(constant(expression, scope)));
  }

  private static String constant(final SyntaxNode expression, final TypeScope scope) {
    final String text = Syntax.text(expression);
    if (text.length() > 1 && text.startsWith(QUOTE) && text.endsWith(QUOTE)) {
      return text.substring(1, text.length() - 1);
    }
    if (text.endsWith(CLASS_SUFFIX)) {
      final String written = text.substring(0, text.length() - CLASS_SUFFIX.length());
      return FactRefs.display(scope.resolve(List.of(written.split("\\."))).orElse(written));
    }
    return text.matches("[A-Za-z_][\\w.]*") ? text.substring(text.lastIndexOf('.') + 1) : text;
  }
}

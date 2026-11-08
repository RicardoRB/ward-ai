package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;

/**
 * The type qualifiers a file declares and where it applies them. A qualifier is an annotation
 * declared in the file and marked {@code @TypeQualifier}; an annotation from another file or a jar
 * is not known to be one, so it is not used.
 *
 * @param qualifiers the qualifiers by simple name
 * @param methods the methods by key {@code Type#name(count)}
 * @param fields what each field is declared as, by {@code Type#name}
 */
record QualifierScope(
    Map<String, Qualifier> qualifiers,
    Map<String, Method> methods,
    Map<String, Map<String, QualWhen>> fields) {

  /**
   * A type qualifier.
   *
   * @param name the simple name of the annotation
   * @param strict whether it has no {@code when} element, so every unknown value counts against it
   * @param defaultWhen the {@code when} of an application that does not give one
   */
  record Qualifier(String name, boolean strict, QualWhen defaultWhen) {}

  /**
   * What a method declares for each qualifier.
   *
   * @param returns the qualifier on its return value
   * @param parameters the qualifiers on each parameter, in order
   */
  record Method(Map<String, QualWhen> returns, List<Map<String, QualWhen>> parameters) {
    Method {
      returns = Map.copyOf(returns);
      parameters = List.copyOf(parameters);
    }
  }

  QualifierScope {
    qualifiers = Map.copyOf(qualifiers);
    methods = Map.copyOf(methods);
    fields = Map.copyOf(fields);
  }

  /** Reads the qualifiers and their uses from {@code root}. */
  static QualifierScope of(final SyntaxNode root) {
    final Map<String, Qualifier> qualifiers = QualifierReader.qualifiers(root);
    if (qualifiers.isEmpty()) {
      return new QualifierScope(qualifiers, Map.of(), Map.of());
    }
    return new QualifierScope(
        qualifiers,
        QualifierMembers.methods(root, qualifiers),
        QualifierMembers.fields(root, qualifiers));
  }
}

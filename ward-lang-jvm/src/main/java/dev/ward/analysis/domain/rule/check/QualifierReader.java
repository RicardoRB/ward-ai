package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reads type qualifier declarations and their applications from the syntax of a file. */
final class QualifierReader {

  private static final String ANNOTATION_TYPE = "annotationTypeDeclaration";
  private static final String IDENTIFIER = "identifier";
  private static final String WHEN = "when";
  private static final String BODY_ENTRY = "classBodyDeclaration";

  private QualifierReader() {}

  /** Returns the annotations declared in {@code root} that are marked as type qualifiers. */
  static Map<String, QualifierScope.Qualifier> qualifiers(final SyntaxNode root) {
    final Map<String, QualifierScope.Qualifier> out = new HashMap<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(ANNOTATION_TYPE))) {
      final SyntaxNode holder = holder(visit.ancestors());
      final boolean marked =
          holder != null
              && NullSyntax.annotations(holder).stream()
                  .map(NullSyntax::simpleName)
                  .anyMatch("TypeQualifier"::equals);
      final List<SyntaxNode> elements = elements(visit.node());
      if (marked && elements.stream().noneMatch(e -> "value".equals(elementName(e)))) {
        final String name =
            NullSyntax.first(visit.node(), IDENTIFIER).map(SyntaxWalk::text).orElse("");
        out.put(name, qualifier(name, elements));
      }
    }
    return out;
  }

  private static QualifierScope.Qualifier qualifier(
      final String name, final List<SyntaxNode> elements) {
    final Optional<SyntaxNode> when =
        elements.stream().filter(e -> WHEN.equals(elementName(e))).findFirst();
    final QualWhen fallback = when.flatMap(QualifierReader::defaultOf).orElse(QualWhen.ALWAYS);
    return new QualifierScope.Qualifier(name, when.isEmpty(), fallback);
  }

  private static Optional<QualWhen> defaultOf(final SyntaxNode element) {
    return SyntaxWalk.descendants(element).stream()
        .filter(node -> "defaultValue".equals(node.kind()))
        .findFirst()
        .flatMap(value -> QualWhen.parse(SyntaxWalk.text(value).replace("default", "")));
  }

  private static List<SyntaxNode> elements(final SyntaxNode declaration) {
    return SyntaxWalk.descendants(declaration).stream()
        .filter(node -> "annotationMethodRest".equals(node.kind()))
        .toList();
  }

  static String elementName(final SyntaxNode element) {
    return NullSyntax.first(element, IDENTIFIER).map(SyntaxWalk::text).orElse("");
  }

  static SyntaxNode holder(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final String kind = ancestors.get(index).kind();
      if ("typeDeclaration".equals(kind) || BODY_ENTRY.equals(kind)) {
        return ancestors.get(index);
      }
    }
    return null;
  }

  /** Returns what the annotations of a declaration say for each qualifier. */
  static Map<String, QualWhen> applied(
      final List<SyntaxNode> annotations, final Map<String, QualifierScope.Qualifier> known) {
    final Map<String, QualWhen> out = new HashMap<>();
    for (final SyntaxNode annotation : annotations) {
      final QualifierScope.Qualifier qualifier = known.get(NullSyntax.simpleName(annotation));
      if (qualifier != null) {
        whenOf(annotation, qualifier).ifPresent(when -> out.put(qualifier.name(), when));
      }
    }
    return out;
  }

  private static Optional<QualWhen> whenOf(
      final SyntaxNode annotation, final QualifierScope.Qualifier qualifier) {
    final Optional<SyntaxNode> given =
        SyntaxWalk.descendants(annotation).stream()
            .filter(node -> "annotationFieldValue".equals(node.kind()))
            .filter(node -> WHEN.equals(elementName(node)))
            .findFirst();
    if (given.isEmpty()) {
      return Optional.of(qualifier.defaultWhen());
    }
    return NullSyntax.first(given.get(), "annotationValue")
        .flatMap(value -> QualWhen.parse(SyntaxWalk.text(value)));
  }
}

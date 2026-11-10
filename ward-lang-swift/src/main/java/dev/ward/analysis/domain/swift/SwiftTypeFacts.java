package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Answers the type facts of Swift natively, from the declarations, extensions and conformances of
 * every parsed project file and from annotations and initializers. It reads no compiler and no SDK:
 * a name it cannot settle from the project is unknown, and so is every fact that depends on one.
 */
public final class SwiftTypeFacts implements TypeFacts {

  private static final String TYPE_IDENTIFIER = "type_identifier";

  private final SwiftTypeIndex index;
  private final SwiftTypeRefs refs;
  private final SwiftTypeExpressions expressions;

  /**
   * Creates the facts over the project's modules and its parsed files, keyed by project-relative
   * path.
   *
   * @throws NullPointerException if the layout or the trees are missing
   */
  public SwiftTypeFacts(final SwiftProjectLayout layout, final Map<String, SyntaxNode> trees) {
    this.index =
        SwiftTypeIndex.build(
            Objects.requireNonNull(layout, "layout"), Objects.requireNonNull(trees, "trees"));
    this.refs = new SwiftTypeRefs(index);
    this.expressions = new SwiftTypeExpressions(index, refs);
  }

  @Override
  public UnitTypeFacts factsOf(
      final SourceUnit unit, final List<Span> spans, final Set<FactKind> kinds) {
    if (!index.has(unit.path()) || spans.isEmpty()) {
      return UnitTypeFacts.none();
    }
    final Set<String> wanted = spans.stream().map(UnitTypeFacts::key).collect(Collectors.toSet());
    final Map<String, SpanFacts> answers = new HashMap<>();
    for (final SwiftTypeSyntax.Visit visit : index.visits(unit.path())) {
      final String key = UnitTypeFacts.key(visit.node().span());
      if (wanted.contains(key) && !visit.inExtension()) {
        final SpanFacts fact = factsAt(unit.path(), visit, kinds);
        if (fact.type() != null || fact.shape() != null) {
          answers.putIfAbsent(key, fact);
        }
      }
    }
    return new UnitTypeFacts(answers);
  }

  private SpanFacts factsAt(
      final String file, final SwiftTypeSyntax.Visit visit, final Set<FactKind> kinds) {
    final TypeRef type = kinds.contains(FactKind.TYPE) ? typeOf(file, visit) : null;
    final TypeShape shape = kinds.contains(FactKind.SHAPE) ? shapeOf(file, visit) : null;
    return new SpanFacts(type, shape, null, List.of(), List.of(), List.of(), List.of(), null, null);
  }

  private TypeRef typeOf(final String file, final SwiftTypeSyntax.Visit visit) {
    final SyntaxNode node = visit.node();
    if (TYPE_IDENTIFIER.equals(node.kind()) && SwiftTypeSyntax.isChained(visit)) {
      return null;
    }
    return switch (node.kind()) {
      case TYPE_IDENTIFIER, "type_", "array_type" -> refs.of(node, file, visit.scope());
      case "pattern_initializer" -> expressions.bindingType(node, file, visit.scope());
      case "postfix_expression" -> expressions.constructorType(node, file, visit.scope());
      default -> null;
    };
  }

  private TypeShape shapeOf(final String file, final SwiftTypeSyntax.Visit visit) {
    final SyntaxNode node = visit.node();
    if (SwiftTypeSyntax.isTypeDeclaration(node.kind())) {
      return SwiftTypeSyntax.nameOf(node)
          .flatMap(name -> index.declaredAt(file, SwiftTypeSyntax.qualify(visit.scope(), name)))
          .map(index::shapeOf)
          .orElse(null);
    }
    if (TYPE_IDENTIFIER.equals(node.kind()) && !SwiftTypeSyntax.isChained(visit)) {
      return refs.declOf(node, file, visit.scope()).map(index::shapeOf).orElse(null);
    }
    return null;
  }
}

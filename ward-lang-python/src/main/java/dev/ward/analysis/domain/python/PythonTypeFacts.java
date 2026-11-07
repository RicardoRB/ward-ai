package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.scope.UnitScopes;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The native type facts of Python: annotations, {@code .pyi} stubs and initializer inference, with
 * no external process. It answers the expression type and the call target; every other kind is
 * unknown.
 */
public final class PythonTypeFacts implements TypeFacts {

  private final LanguageFrontend parser;
  private final PythonStubs stubs;
  private final PythonScopes analysis = new PythonScopes(environments -> Set.of());

  /** Creates the facts over the parser of the file and the stubs it consults. */
  public PythonTypeFacts(final LanguageFrontend parser, final PythonStubs stubs) {
    this.parser = parser;
    this.stubs = stubs;
  }

  @Override
  public UnitTypeFacts factsOf(
      final SourceUnit unit, final List<Span> spans, final Set<FactKind> kinds) {
    final SyntaxNode tree = parser.parse(unit).tree();
    final UnitScopes scopes = analysis.analyze(unit, tree);
    final PythonTypeIndex index = new PythonTypeIndex(tree, scopes);
    final PythonExpressionTypes types = new PythonExpressionTypes(index, stubs);
    final Map<String, SpanFacts> facts = new HashMap<>();
    for (final Span span : spans) {
      final SpanFacts known = index.node(span).map(node -> facts(node, kinds, types)).orElse(null);
      if (known != null && isKnown(known)) {
        facts.put(UnitTypeFacts.key(span), known);
      }
    }
    return new UnitTypeFacts(facts);
  }

  private static SpanFacts facts(
      final SyntaxNode node, final Set<FactKind> kinds, final PythonExpressionTypes types) {
    final TypeRef type = kinds.contains(FactKind.TYPE) ? types.typeOf(node).orElse(null) : null;
    final CallTarget call = kinds.contains(FactKind.CALL) ? types.callOf(node).orElse(null) : null;
    return new SpanFacts(type, null, call, List.of(), List.of(), List.of(), List.of(), null);
  }

  private static boolean isKnown(final SpanFacts facts) {
    return facts.type() != null || facts.call() != null;
  }
}

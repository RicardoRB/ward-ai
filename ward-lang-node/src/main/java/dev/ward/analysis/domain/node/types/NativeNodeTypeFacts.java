package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.scope.UnitScopes;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Answers type facts for TypeScript and JavaScript units with no compiler and no process: the unit
 * is parsed, its names are resolved on the scope model, and the declarations of other files, of
 * packages and of the TypeScript library are read from their {@code .d.ts} text. A fact that would
 * need full checking is left out.
 */
public final class NativeNodeTypeFacts implements TypeFacts {
  private static final Logger LOGGER = Logger.getLogger(NativeNodeTypeFacts.class.getName());

  private final Program program;
  private final LanguageFrontend frontend;
  private final ScopeAnalysis scopes;

  /**
   * Creates the facts over the declarations of a project.
   *
   * @param program the declarations the units can see
   * @param frontend the parser
   * @param scopes the scope analysis of TypeScript and JavaScript
   */
  public NativeNodeTypeFacts(
      final Program program, final LanguageFrontend frontend, final ScopeAnalysis scopes) {
    this.program = program;
    this.frontend = frontend;
    this.scopes = scopes;
  }

  @Override
  public UnitTypeFacts factsOf(
      final SourceUnit unit, final List<Span> spans, final Set<FactKind> kinds) {
    final boolean node =
        unit.language() == AnalysisLanguage.TYPESCRIPT
            || unit.language() == AnalysisLanguage.JAVASCRIPT;
    if (!node || spans.isEmpty() || kinds.isEmpty()) {
      return UnitTypeFacts.none();
    }
    final SyntaxNode parsed = frontend.parse(unit).tree();
    final UnitScopes unitScopes = scopes.analyze(unit, parsed);
    final SyntaxNode tree = Reassociate.of(parsed);
    final Module module = program.unitModule(unit.path(), DeclReader.read(tree, unit.text()));
    final UnitModel model = new UnitModel(unit, tree, unitScopes, module);
    final ExprTyper typer = new ExprTyper(model, program, new Members(program));
    final FactBuilder builder = new FactBuilder(model, typer);
    final Map<String, SpanFacts> facts = new HashMap<>();
    for (final Span span : spans) {
      final SpanFacts known = builder.at(span, kinds);
      if (known != null) {
        facts.put(UnitTypeFacts.key(span), known);
      }
    }
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine("event=native-type-facts language=" + unit.language() + " spans=" + spans.size());
    }
    return new UnitTypeFacts(facts);
  }
}

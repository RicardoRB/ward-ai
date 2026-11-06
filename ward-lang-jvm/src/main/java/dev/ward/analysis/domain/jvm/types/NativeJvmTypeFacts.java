package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Answers type facts for Java and Kotlin units with no compiler and no process: the unit is parsed,
 * its names are resolved on the scope model, and the types of the project's sources and of the
 * classes in the libraries and the JDK are read directly. A fact it cannot determine is left out.
 */
public final class NativeJvmTypeFacts implements TypeFacts {
  private static final Logger LOGGER = Logger.getLogger(NativeJvmTypeFacts.class.getName());

  private final JvmClasses classes;
  private final LanguageFrontend frontend;
  private final Map<AnalysisLanguage, ScopeAnalysis> scopes;

  /**
   * Creates the facts over the classes of a project.
   *
   * @param classes the classes the units can see
   * @param frontend the parser
   * @param scopes the scope analysis of each language it answers for
   */
  public NativeJvmTypeFacts(
      final JvmClasses classes,
      final LanguageFrontend frontend,
      final Map<AnalysisLanguage, ScopeAnalysis> scopes) {
    this.classes = classes;
    this.frontend = frontend;
    this.scopes = Map.copyOf(scopes);
  }

  @Override
  public UnitTypeFacts factsOf(
      final SourceUnit unit, final List<Span> spans, final Set<FactKind> kinds) {
    final ScopeAnalysis analysis = scopes.get(unit.language());
    if (analysis == null || spans.isEmpty() || kinds.isEmpty()) {
      return UnitTypeFacts.none();
    }
    final SyntaxNode tree = frontend.parse(unit).tree();
    final Session session;
    if (unit.language() == AnalysisLanguage.JAVA) {
      final JavaUnitModel model = JavaUnitModel.of(tree, classes::exists);
      session = new Session(classes.withLocal(model.declared()), analysis.analyze(unit, tree));
      new JavaTyper(session).run(model);
    } else {
      final KotlinUnitModel model = KotlinUnitModel.of(tree, classes::exists);
      session = new Session(classes.withLocal(model.declared()), analysis.analyze(unit, tree));
      new KotlinTyper(session).run(model, tree);
    }
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine("event=native-type-facts language=" + unit.language() + " spans=" + spans.size());
    }
    return new FactTable(session).answer(spans, kinds);
  }
}

package dev.ward.infrastructure.analysis;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.jvm.JavaControlFlow;
import dev.ward.analysis.domain.jvm.JavaScopeAnalysis;
import dev.ward.analysis.domain.jvm.KotlinScopeAnalysis;
import dev.ward.analysis.domain.node.NodeControlFlow;
import dev.ward.analysis.domain.node.NodeScopeAnalysis;
import dev.ward.analysis.domain.port.ControlFlowLowering;
import dev.ward.analysis.domain.port.ControlFlowResult;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.python.PythonScopes;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.scope.CachedScopes;
import dev.ward.analysis.domain.swift.SwiftScopeAnalysis;
import java.util.List;
import java.util.Set;

/**
 * The language frontends of the {@code ward} gate: what each language provides to rules. Scopes are
 * built once per unit and shared by every rule and gate that reads them.
 */
public final class GateFrontends {

  private static final ControlFlowLowering NO_GRAPHS =
      (unit, tree) -> new ControlFlowResult(List.of(), List.of());
  private static final Set<RuleCapability> SYNTAX =
      Set.of(RuleCapability.SYNTAX, RuleCapability.SCOPES);
  private static final Set<RuleCapability> SYMBOLS =
      Set.of(RuleCapability.SYNTAX, RuleCapability.SYMBOLS, RuleCapability.SCOPES);
  private static final Set<RuleCapability> FLOW =
      Set.of(
          RuleCapability.SYNTAX,
          RuleCapability.SYMBOLS,
          RuleCapability.CFG,
          RuleCapability.DATAFLOW,
          RuleCapability.SCOPES);

  private GateFrontends() {}

  /** Creates the support of every language over one parser. */
  public static List<FrontendSupport> create(final LanguageFrontend frontend) {
    return List.of(
        new FrontendSupport(
            AnalysisLanguage.JAVA,
            frontend,
            FLOW,
            new JavaControlFlow(),
            new CachedScopes(new JavaScopeAnalysis(ClasspathBuiltinNames.java()))),
        new FrontendSupport(
            AnalysisLanguage.PYTHON,
            frontend,
            SYMBOLS,
            NO_GRAPHS,
            new CachedScopes(new PythonScopes(ClasspathBuiltinNames.python()))),
        new FrontendSupport(
            AnalysisLanguage.TYPESCRIPT,
            frontend,
            FLOW,
            new NodeControlFlow(),
            new CachedScopes(new NodeScopeAnalysis(ClasspathBuiltinNames.typescript()))),
        new FrontendSupport(
            AnalysisLanguage.JAVASCRIPT,
            frontend,
            FLOW,
            new NodeControlFlow(),
            new CachedScopes(new NodeScopeAnalysis(ClasspathBuiltinNames.javascript()))),
        new FrontendSupport(
            AnalysisLanguage.SWIFT,
            frontend,
            SYMBOLS,
            NO_GRAPHS,
            new CachedScopes(new SwiftScopeAnalysis(ClasspathBuiltinNames.swift()))),
        new FrontendSupport(
            AnalysisLanguage.KOTLIN,
            frontend,
            SYNTAX,
            NO_GRAPHS,
            new CachedScopes(new KotlinScopeAnalysis(ClasspathBuiltinNames.kotlin()))));
  }
}

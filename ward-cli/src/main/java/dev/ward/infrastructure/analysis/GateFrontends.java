package dev.ward.infrastructure.analysis;

import dev.ward.analysis.application.android.AndroidXmlFrontend;
import dev.ward.analysis.application.openapi.OpenApiLanguageFrontend;
import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.jvm.JavaControlFlow;
import dev.ward.analysis.domain.jvm.JavaScopeAnalysis;
import dev.ward.analysis.domain.jvm.KotlinControlFlow;
import dev.ward.analysis.domain.jvm.KotlinScopeAnalysis;
import dev.ward.analysis.domain.node.NodeControlFlow;
import dev.ward.analysis.domain.node.NodeScopeAnalysis;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.python.PythonControlFlow;
import dev.ward.analysis.domain.python.PythonScopes;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.check.NodeImplicitUse;
import dev.ward.analysis.domain.scope.CachedScopes;
import dev.ward.analysis.domain.swift.SwiftControlFlow;
import dev.ward.analysis.domain.swift.SwiftScopeAnalysis;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The language frontends of the {@code ward} gate: what each language provides to rules. Scopes are
 * built once per unit and shared by every rule and gate that reads them.
 */
public final class GateFrontends {
  /** The manifests that the TypeScript and JavaScript project checks read. */
  private static final Set<String> NODE_MANIFESTS =
      Stream.concat(
          Stream.of("package.json", "pnpm-workspace.yaml", "index.html"),
          NodeImplicitUse.configFileNames().stream()
      )
          .collect(Collectors.toUnmodifiableSet());

  private GateFrontends() {}

  /** Creates the support of every language over one parser. */
  public static List<FrontendSupport> create(final LanguageFrontend frontend) {
    return Stream.concat(
        scriptingLanguages(frontend).stream(),
        compiledLanguages(frontend).stream()
    )
        .toList();
  }

  private static List<FrontendSupport> scriptingLanguages(final LanguageFrontend frontend) {
    final Set<RuleCapability> flow = flowCapabilities();
    return List.of(
        new FrontendSupport(
            AnalysisLanguage.JAVA,
            frontend,
            flow,
            new JavaControlFlow(),
            new CachedScopes(new JavaScopeAnalysis(ClasspathBuiltinNames.java()))
        ),
        new FrontendSupport(
            AnalysisLanguage.PYTHON,
            frontend,
            flow,
            new PythonControlFlow(),
            new CachedScopes(new PythonScopes(ClasspathBuiltinNames.python()))
        ),
        new FrontendSupport(
            AnalysisLanguage.TYPESCRIPT,
            frontend,
            flow,
            new NodeControlFlow(),
            new CachedScopes(new NodeScopeAnalysis(ClasspathBuiltinNames.typescript()))
        )
            .withManifests(NODE_MANIFESTS)
            .withProjectLanguages(Set.of(AnalysisLanguage.JAVASCRIPT)),
        new FrontendSupport(
            AnalysisLanguage.JAVASCRIPT,
            frontend,
            flow,
            new NodeControlFlow(),
            new CachedScopes(new NodeScopeAnalysis(ClasspathBuiltinNames.javascript()))
        )
            .withManifests(NODE_MANIFESTS)
    );
  }

  private static List<FrontendSupport> compiledLanguages(final LanguageFrontend frontend) {
    final Set<RuleCapability> swift = swiftCapabilities();
    final Set<RuleCapability> graphs = graphCapabilities();
    return List.of(
        new FrontendSupport(
            AnalysisLanguage.SWIFT,
            frontend,
            swift,
            new SwiftControlFlow(),
            new CachedScopes(new SwiftScopeAnalysis(ClasspathBuiltinNames.swift()))
        ),
        new FrontendSupport(
            AnalysisLanguage.KOTLIN,
            frontend,
            graphs,
            new KotlinControlFlow(),
            new CachedScopes(new KotlinScopeAnalysis(ClasspathBuiltinNames.kotlin()))
        ),
        new FrontendSupport(
            AnalysisLanguage.OPENAPI,
            new OpenApiLanguageFrontend(),
            Set.of(RuleCapability.SYNTAX)
        ),
        new FrontendSupport(
            AnalysisLanguage.ANDROID,
            new AndroidXmlFrontend(),
            Set.of(RuleCapability.SYNTAX)
        )
            .withProjectLanguages(Set.of(AnalysisLanguage.JAVA, AnalysisLanguage.KOTLIN))
    );
  }

  // The sets are built per call, not in static fields: a native image must not freeze enum sets.
  private static Set<RuleCapability> swiftCapabilities() {
    return Set.of(
        RuleCapability.SYNTAX,
        RuleCapability.SYMBOLS,
        RuleCapability.SCOPES,
        RuleCapability.CFG,
        RuleCapability.DATAFLOW,
        RuleCapability.TAINT
    );
  }

  private static Set<RuleCapability> graphCapabilities() {
    return Set.of(
        RuleCapability.SYNTAX,
        RuleCapability.CFG,
        RuleCapability.DATAFLOW,
        RuleCapability.TAINT,
        RuleCapability.SCOPES
    );
  }

  private static Set<RuleCapability> flowCapabilities() {
    return Set.of(
        RuleCapability.SYNTAX,
        RuleCapability.SYMBOLS,
        RuleCapability.CFG,
        RuleCapability.DATAFLOW,
        RuleCapability.TAINT,
        RuleCapability.SCOPES
    );
  }
}

package dev.ward.application.gate;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.DependencyResolver;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleMatcher;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fake ports for {@link FocusedAnalysisGateTest}: three Java files, one dependency edge. */
final class FocusedGateFixtures {
  static final String LENGTH = "ward:java/method-length";
  static final String LAYER = "ward:java/edge-probe";
  static final Span AT = new Span(3, 5, 3, 9, 20, 24);
  static final List<String> UNITS = List.of("src/A.java", "src/W.java", "src/C.java");

  /** {@code src/W.java} depends on the changed {@code src/A.java}; {@code src/C.java} is alone. */
  static final List<DependencyEdge> EDGES =
      List.of(new DependencyEdge("src/W.java", "src/A.java", new Reference("A", AT)));

  static final List<RuleDefinition> RULES =
      List.of(
          new RuleDefinition(
              LENGTH, AnalysisLanguage.JAVA, RuleCapability.SYNTAX, AnalysisSeverity.ERROR, "b"),
          new RuleDefinition(
              LAYER, AnalysisLanguage.JAVA, RuleCapability.SYMBOLS, AnalysisSeverity.ERROR, "b"));
  static final Map<String, List<RuleMatch>> MATCHES =
      Map.of(LENGTH, List.of(new RuleMatch(AT, "long")), LAYER, List.of(new RuleMatch(AT, "edge")));

  private FocusedGateFixtures() {}

  /** The gate over the fake ports, reporting only the findings that touch {@code focus}. */
  static AnalysisGate gate(final List<String> focus) {
    final LanguageFrontend frontend =
        unit -> new ParseResult(new SyntaxNode("file", AT, Map.of(), List.of()), List.of());
    final RuleMatcher matcher =
        definition ->
            new CompiledRule() {
              @Override
              public RuleDefinition definition() {
                return definition;
              }

              @Override
              public List<RuleMatch> matches(final SyntaxNode root) {
                return MATCHES.get(definition.id());
              }
            };
    final DependencyResolver resolver = summaries -> EDGES;
    final DependencySupport support =
        new DependencySupport(
            AnalysisLanguage.JAVA,
            (unit, tree) -> new FileSummary(unit.path(), List.of(), List.of(), List.of()),
            resolver);
    final AnalysisPorts ports =
        new AnalysisPorts(
            () -> new AnalysisRuleLoad(RULES, List.of()),
            matcher,
            (root, languages) -> units(),
            List.of(
                new FrontendSupport(
                    AnalysisLanguage.JAVA,
                    frontend,
                    Set.of(RuleCapability.SYNTAX, RuleCapability.SYMBOLS))),
            root -> AnalysisCacheFixtures.NO_CACHE,
            AnalysisCacheFixtures.codec(),
            "test",
            (root, units) -> List.of(support),
            (unused, classpath) -> Map.of());
    return new AnalysisGate("ward", RULES, ports, null, focus);
  }

  private static List<SourceUnit> units() {
    return UNITS.stream().map(path -> new SourceUnit(path, AnalysisLanguage.JAVA, "")).toList();
  }
}

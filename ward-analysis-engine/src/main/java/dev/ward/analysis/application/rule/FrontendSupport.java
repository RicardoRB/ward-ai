package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.ControlFlowLowering;
import dev.ward.analysis.domain.port.ControlFlowResult;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.scope.NoScopes;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A language frontend, the capabilities it provides and the control-flow lowering it offers. Rules
 * run only when their capability is in this set.
 *
 * @param language the language the frontend parses
 * @param frontend the parser
 * @param capabilities the capabilities the frontend provides
 * @param lowering the control-flow lowering, which yields no graphs for a language without one
 * @param scopes the scope analysis, which gives empty scopes for a language without one
 * @param types the native type-fact source, empty for a language without one
 */
public record FrontendSupport(
    AnalysisLanguage language,
    LanguageFrontend frontend,
    Set<RuleCapability> capabilities,
    ControlFlowLowering lowering,
    ScopeAnalysis scopes,
    Optional<TypeFacts> types) {

  /** A lowering for a language that has no control-flow graphs yet. */
  private static final ControlFlowLowering NO_GRAPHS =
      (unit, tree) -> new ControlFlowResult(List.of(), List.of());

  /**
   * Creates a frontend with a scope analysis and no type facts.
   *
   * @param language the language the frontend parses
   * @param frontend the parser
   * @param capabilities the capabilities the frontend provides
   * @param lowering the control-flow lowering
   * @param scopes the scope analysis
   */
  public FrontendSupport(
      final AnalysisLanguage language,
      final LanguageFrontend frontend,
      final Set<RuleCapability> capabilities,
      final ControlFlowLowering lowering,
      final ScopeAnalysis scopes) {
    this(language, frontend, capabilities, lowering, scopes, Optional.empty());
  }

  /**
   * Creates a frontend that provides no control-flow graphs.
   *
   * @param language the language the frontend parses
   * @param frontend the parser
   * @param capabilities the capabilities the frontend provides
   */
  public FrontendSupport(
      final AnalysisLanguage language,
      final LanguageFrontend frontend,
      final Set<RuleCapability> capabilities) {
    this(language, frontend, capabilities, NO_GRAPHS, NoScopes.INSTANCE);
  }

  /**
   * Creates a frontend that provides control-flow graphs and no scopes.
   *
   * @param language the language the frontend parses
   * @param frontend the parser
   * @param capabilities the capabilities the frontend provides
   * @param lowering the control-flow lowering
   */
  public FrontendSupport(
      final AnalysisLanguage language,
      final LanguageFrontend frontend,
      final Set<RuleCapability> capabilities,
      final ControlFlowLowering lowering) {
    this(language, frontend, capabilities, lowering, NoScopes.INSTANCE);
  }

  /**
   * Copies the capabilities.
   *
   * @throws NullPointerException if a field is missing
   */
  public FrontendSupport {
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(frontend, "frontend");
    Objects.requireNonNull(lowering, "lowering");
    Objects.requireNonNull(scopes, "scopes");
    Objects.requireNonNull(types, "types");
    capabilities = Set.copyOf(capabilities);
  }
}

package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.ControlFlowLowering;
import dev.ward.analysis.domain.port.ControlFlowResult;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.rule.RuleCapability;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A language frontend, the capabilities it provides and the control-flow lowering it offers. Rules
 * run only when their capability is in this set.
 *
 * @param language the language the frontend parses
 * @param frontend the parser
 * @param capabilities the capabilities the frontend provides
 * @param lowering the control-flow lowering, which yields no graphs for a language without one
 */
public record FrontendSupport(
    AnalysisLanguage language,
    LanguageFrontend frontend,
    Set<RuleCapability> capabilities,
    ControlFlowLowering lowering) {

  /** A lowering for a language that has no control-flow graphs yet. */
  private static final ControlFlowLowering NO_GRAPHS =
      (unit, tree) -> new ControlFlowResult(List.of(), List.of());

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
    this(language, frontend, capabilities, NO_GRAPHS);
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
    capabilities = Set.copyOf(capabilities);
  }
}

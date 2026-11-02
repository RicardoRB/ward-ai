package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.rule.RuleCapability;
import java.util.Objects;
import java.util.Set;

/**
 * A language frontend and the capabilities it provides. Rules run only when their capability is in
 * this set.
 *
 * @param language the language the frontend parses
 * @param frontend the parser
 * @param capabilities the capabilities the frontend provides
 */
public record FrontendSupport(
    AnalysisLanguage language, LanguageFrontend frontend, Set<RuleCapability> capabilities) {

  /**
   * Copies the capabilities.
   *
   * @throws NullPointerException if a field is missing
   */
  public FrontendSupport {
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(frontend, "frontend");
    capabilities = Set.copyOf(capabilities);
  }
}

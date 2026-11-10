package dev.ward.analysis.domain.port;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.Objects;

/**
 * The dependency adapters of one language. A language without one contributes no edges.
 *
 * @param language the language the adapters read
 * @param summarizer builds the per-file summaries
 * @param resolver resolves the references of a run
 */
public record DependencySupport(
    AnalysisLanguage language, FileSummarizer summarizer, DependencyResolver resolver) {

  /**
   * Validates the required fields.
   *
   * @throws NullPointerException if a field is missing
   */
  public DependencySupport {
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(summarizer, "summarizer");
    Objects.requireNonNull(resolver, "resolver");
  }
}

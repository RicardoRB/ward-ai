package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;

/**
 * Parses Java as a compilation unit, and as a compact source file (JEP 512) only when the unit
 * does not parse, so regular files keep their diagnostics.
 */
final class JavaSession implements LanguageFrontend {

  private final LanguageFrontend unit;
  private final LanguageFrontend compact;

  /**
   * Binds the sessions of the two start rules.
   *
   * @param unit the session of the compilation unit rule
   * @param compact the session of the compact source rule
   */
  JavaSession(final LanguageFrontend unit, final LanguageFrontend compact) {
    this.unit = unit;
    this.compact = compact;
  }

  @Override
  public ParseResult parse(final SourceUnit source) {
    final ParseResult regular = unit.parse(source);
    if (regular.diagnostics().isEmpty()) {
      return regular;
    }
    final ParseResult compactResult = compact.parse(source);
    return compactResult.diagnostics().isEmpty() ? compactResult : regular;
  }
}

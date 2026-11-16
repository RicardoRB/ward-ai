package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;

/**
 * Lends each parsing thread its own sessions, one per language: a lexer and a parser are not
 * thread-safe. A set of sessions is created when no idle one is left, so there are as many sets as
 * threads that parsed at the same time, and the session of a language is created when first needed.
 */
final class SessionPool {
  private final Function<AnalysisLanguage, LanguageFrontend> create;
  private final Deque<Map<AnalysisLanguage, LanguageFrontend>> idle =
      new ConcurrentLinkedDeque<>();

  /** Creates an empty pool that makes the session of a language with {@code create}. */
  SessionPool(final Function<AnalysisLanguage, LanguageFrontend> create) {
    this.create = create;
  }

  /** Parses {@code unit} with sessions that no other thread uses until they are handed back. */
  ParseResult parse(final SourceUnit unit) {
    final Map<AnalysisLanguage, LanguageFrontend> borrowed = idle.pollFirst();
    final Map<AnalysisLanguage, LanguageFrontend> sessions =
        borrowed == null ? new EnumMap<>(AnalysisLanguage.class) : borrowed;
    try {
      return sessions.computeIfAbsent(unit.language(), create).parse(unit);
    } finally {
      // The most recently used set goes first, so one thread keeps reusing one set.
      idle.addFirst(sessions);
    }
  }
}

package dev.ward.application.format;

import dev.ward.analysis.domain.format.FormatResult;
import dev.ward.analysis.domain.format.FormatSafety;
import dev.ward.analysis.domain.format.FormatterEntry;
import dev.ward.analysis.domain.format.FormatterRegistry;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.application.format.FormatFileOutcome.Verdict;
import dev.ward.domain.config.WardException;
import dev.ward.domain.format.FormatLayouts;
import dev.ward.domain.format.FormatSettings;
import dev.ward.domain.format.FormatTarget;
import dev.ward.domain.format.SourceReader;
import dev.ward.domain.format.SourceSink;
import dev.ward.domain.format.SourceText;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * Formats one file: runs the formatter through {@link FormatSafety}, applies the file layout, and
 * compares the result with the file. The file is written only when the caller asks for it.
 */
public final class FormatFileEvaluator {
  /** The message of a finding for a file that the formatter would change. */
  static final String NOT_FORMATTED = "file is not formatted";

  /** The reason a symbolic link is refused: the link is never replaced by a regular file. */
  static final String SYMBOLIC_LINK = "symbolic link, not rewritten";

  private static final Logger LOGGER = Logger.getLogger(FormatFileEvaluator.class.getName());
  private static final String CLEAN = "clean";
  private static final String LF = "\n";
  private static final String CRLF = "\r\n";
  private static final int NO_LINE = 0;

  private final FormatterRegistry registry;
  private final SourceReader reader;
  private final SourceSink sink;
  private final String wardVersion;

  /** Creates the evaluator over the formatters and the file adapter. */
  @Inject
  public FormatFileEvaluator(
      final FormatterRegistry registry,
      final SourceReader reader,
      final SourceSink sink,
      @Named("ward.version") final String wardVersion) {
    this.registry = registry;
    this.reader = reader;
    this.sink = sink;
    this.wardVersion = wardVersion;
  }

  /**
   * Formats one file and reports what happened to it. A read, write or configuration problem is
   * reported as a failed outcome, never thrown.
   */
  FormatFileOutcome process(
      final Path root,
      final FormatTarget target,
      final Optional<AnalysisCache> cache,
      final boolean write) {
    try {
      final FormatFileOutcome outcome = evaluate(root, target, cache, write);
      LOGGER.fine(() -> "event=format-file verdict=" + outcome.verdict());
      return outcome;
    } catch (final WardException exception) {
      LOGGER.fine("event=format-failed");
      return new FormatFileOutcome(
          target.relativePath(), Verdict.FAILED, NO_LINE, exception.getMessage());
    }
  }

  private FormatFileOutcome evaluate(
      final Path root,
      final FormatTarget target,
      final Optional<AnalysisCache> cache,
      final boolean write) {
    final String path = target.relativePath();
    final SourceText original = reader.read(root.resolve(path));
    final String lf = original.text().replace(CRLF, LF);
    final Optional<String> key = cache.map(ignored -> FormatCacheKey.of(wardVersion, target, lf));
    if (key.isPresent() && cache.orElseThrow().read(key.get()).isPresent()) {
      return unchanged(path);
    }
    final FormatResult result = formatSafely(target, lf);
    if (result instanceof FormatResult.Refused refused) {
      return new FormatFileOutcome(path, Verdict.REFUSED, refused.line(), refused.reason());
    }
    final String formatted = result instanceof FormatResult.Formatted done ? done.text() : lf;
    final FormatSettings settings = target.settings();
    final String laid =
        FormatLayouts.apply(
            formatted, settings.layout(), FormatLayouts.lineEndingOf(original.text()));
    final boolean bom = FormatLayouts.byteOrderMark(settings.layout(), original.byteOrderMark());
    if (laid.equals(original.text()) && bom == original.byteOrderMark()) {
      key.ifPresent(value -> cache.orElseThrow().write(value, CLEAN));
      return unchanged(path);
    }
    final int line = FormatLayouts.firstDifferentLine(original.text(), laid);
    if (!write) {
      return new FormatFileOutcome(path, Verdict.NEEDS_FORMATTING, line, NOT_FORMATTED);
    }
    if (original.symbolicLink()) {
      return new FormatFileOutcome(path, Verdict.REFUSED, line, SYMBOLIC_LINK);
    }
    sink.persist(root.resolve(path), laid, bom);
    return new FormatFileOutcome(path, Verdict.WRITTEN, line, "");
  }

  private FormatResult formatSafely(final FormatTarget target, final String lf) {
    final FormatterEntry entry = formatter(target.language());
    return new FormatSafety(entry.syntax())
        .formatSafely(entry.formatter(), target.settings().options(), lf);
  }

  FormatterEntry formatter(final AnalysisLanguage language) {
    return registry
        .find(language)
        .orElseThrow(() -> new WardException("No formatter for " + language + "."));
  }

  private static FormatFileOutcome unchanged(final String path) {
    return new FormatFileOutcome(path, Verdict.UNCHANGED, NO_LINE, "");
  }
}

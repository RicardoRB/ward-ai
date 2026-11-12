package dev.ward.application.gate;

import dev.ward.analysis.application.rule.TextRules;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.domain.secrets.GitHistory;
import dev.ward.domain.secrets.HistoryPatch;
import dev.ward.domain.secrets.HistoryUnavailableException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Scans the lines that every commit added with the same rules as the working tree. A finding names
 * the file, the line in that commit and the commit in its message. The history is not cached.
 */
final class SecretHistoryScan {
  private static final Logger LOGGER = Logger.getLogger(SecretHistoryScan.class.getName());

  private final GitHistory history;

  SecretHistoryScan(final GitHistory history) {
    this.history = history;
  }

  /** Scans the history of the project at {@code root}; the result holds no source text. */
  RuleRunResult scan(
      final Path root,
      final Optional<LocalDate> since,
      final TextRules rules,
      final Predicate<String> excluded) {
    final List<AnalysisFinding> findings = new ArrayList<>();
    final List<RuleExecutionError> errors = new ArrayList<>();
    final int[] patches = {0};
    try {
      history.patches(
          root,
          since,
          batch ->
              batch.stream()
                  .filter(patch -> !excluded.test(patch.path()))
                  .forEach(
                      patch -> {
                        patches[0]++;
                        scanPatch(patch, rules, findings, errors);
                      }));
    } catch (final HistoryUnavailableException exception) {
      errors.add(new RuleExecutionError(null, null, exception.getMessage()));
    }
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine(
          "event=secrets-history patches="
              + patches[0]
              + " findings="
              + findings.size()
              + " errors="
              + errors.size());
    }
    return new RuleRunResult(findings, errors, List.of());
  }

  private static void scanPatch(
      final HistoryPatch patch,
      final TextRules rules,
      final List<AnalysisFinding> findings,
      final List<RuleExecutionError> errors) {
    final String text =
        String.join("\n", patch.lines().stream().map(HistoryPatch.AddedLine::text).toList()) + "\n";
    final RuleRunResult result = rules.run(List.of(new TextFile(patch.path(), text)));
    result.findings().forEach(finding -> findings.add(inCommit(patch, finding)));
    result
        .executionErrors()
        .forEach(
            error ->
                errors.add(
                    new RuleExecutionError(
                        error.ruleId(),
                        error.path(),
                        error.reason() + " in commit " + patch.commit())));
  }

  /** The finding at the post-image line of the commit, with the commit in its message. */
  private static AnalysisFinding inCommit(final HistoryPatch patch, final AnalysisFinding finding) {
    final Integer line =
        finding.line() == null ? null : patch.lines().get(finding.line() - 1).number();
    return new AnalysisFinding(
        finding.ruleId(),
        finding.severity(),
        finding.message() + " in commit " + patch.commit(),
        finding.file(),
        line,
        finding.column(),
        finding.helpUri(),
        finding.path());
  }

  /** Whether a glob list leaves {@code path} out. */
  static Predicate<String> excluding(final List<Pattern> patterns) {
    return path -> patterns.stream().anyMatch(pattern -> pattern.matcher(path).matches());
  }
}

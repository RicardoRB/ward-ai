package dev.ward.application.gate;

import dev.ward.analysis.application.rule.TextRules;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisFindingOrder;
import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.SecretsOptions;
import dev.ward.domain.config.WardException;
import dev.ward.domain.secrets.PromptFinding;
import dev.ward.domain.secrets.PromptInput;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Scans an agent prompt with the rules of a {@code secrets} check. The message has no path, so
 * rules that read paths skip it and path allowlists do not apply; an attached file keeps its path
 * and the check's {@code exclude-paths}. The prompt is never cached or written anywhere, and a
 * finding holds no text of it.
 */
public final class PromptScan {
  private static final Logger LOGGER = Logger.getLogger(PromptScan.class.getName());

  private final String name;
  private final SecretsOptions options;
  private final RuleSetup setup;
  private final AnalysisPorts ports;

  PromptScan(final GateConfiguration check, final RuleSetup setup, final AnalysisPorts ports) {
    this.name = check.name();
    this.options = check.secrets();
    this.setup = setup;
    this.ports = ports;
  }

  /**
   * Returns where the secret rules match in {@code input}, message first, then each file.
   *
   * @throws WardException if the rules cannot be set up or the prompt cannot be scanned completely;
   *     the message holds no text of the prompt
   */
  public List<PromptFinding> scan(final Path root, final PromptInput input) {
    final RuleSet set = setup.resolve(root, 0);
    final List<String> problems = new ArrayList<>(set.problems());
    final List<Pattern> excluded = SecretExcludes.compile(name, options, problems);
    if (!problems.isEmpty()) {
      throw new WardException(String.join(" ", problems));
    }
    final List<RuleDefinition> rules =
        set.rules().stream().filter(rule -> rule.capability() == RuleCapability.TEXT).toList();
    final List<AnalysisFinding> found = new ArrayList<>();
    final List<String> failures = new ArrayList<>();
    final List<TextFile> texts = new ArrayList<>();
    texts.add(new TextFile(TextFile.NO_PATH, input.message()));
    final List<String> files = PromptFiles.relative(root, input);
    if (!files.isEmpty()) {
      final TextRead read =
          ports.sources().readText(root, files, SecretHistoryScan.excluding(excluded));
      read.errors().forEach(error -> failures.add(describe(error.ruleId(), error.path())));
      texts.addAll(read.files());
    }
    final TextRules text = new TextRules(ports.matcher(), PromptKeywords.relevant(rules, texts));
    collect(text.run(texts), set, found, failures);
    if (!failures.isEmpty()) {
      throw new WardException(
          "Ward could not scan the whole prompt (" + String.join("; ", failures) + ")."
      );
    }
    found.sort(AnalysisFindingOrder.ORDER);
    LOGGER.log(
        Level.INFO,
        () -> "event=prompt-scan files=" + files.size() + " findings=" + found.size()
    );
    return found.stream().map(PromptScan::finding).toList();
  }

  private static void collect(
      final RuleRunResult run,
      final RuleSet set,
      final List<AnalysisFinding> found,
      final List<String> failures
  ) {
    found.addAll(ExcludedFindings.drop(run, set).findings());
    run.executionErrors().forEach(error -> failures.add(describe(error.ruleId(), error.path())));
  }

  private static String describe(final String rule, final String file) {
    return (rule == null ? "no rule" : rule) + (file == null || file.isEmpty() ? "" : " in " + file);
  }

  private static PromptFinding finding(final AnalysisFinding found) {
    final String file = found.file() == null || found.file().isEmpty() ? null : found.file();
    return new PromptFinding(
        file,
        found.ruleId(),
        found.line() == null ? 1 : found.line(),
        found.column() == null ? 1 : found.column()
    );
  }
}

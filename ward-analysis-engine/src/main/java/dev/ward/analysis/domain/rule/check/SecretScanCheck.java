package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Finds secrets in the text of a file with one rule of the Gitleaks default configuration, in the
 * order of Gitleaks 8.30.1: the allowlists of the paths, the path filter, the keywords, the regex,
 * the entropy of the secret, then the allowlists of the findings. A match carries offsets only; the
 * secret lives inside {@link #scan} and is dropped before it returns.
 */
public final class SecretScanCheck implements TextCheck {

  /** The reason of the execution error when a rule goes over its work budget. */
  public static final String OVER_BUDGET = "the rule exceeded its work budget";

  private final SecretRule rule;
  private final List<SecretAllowlist> global;

  /**
   * Creates the check of {@code rule} under the allowlists in {@code global}, which apply to every
   * rule.
   */
  public SecretScanCheck(final SecretRule rule, final List<SecretAllowlist> global) {
    this.rule = Objects.requireNonNull(rule, "rule");
    this.global = List.copyOf(global);
  }

  @Override
  public List<RuleMatch> textMatches(final TextFile file) {
    if (skipsFile(file.path())) {
      return List.of();
    }
    if (rule.path().isPresent()) {
      final boolean onPath = rule.path().get().matcher(file.path()).find();
      if (rule.regex().isEmpty()) {
        return onPath ? List.of(new RuleMatch(null, rule.message())) : List.of();
      }
      if (!onPath) {
        return List.of();
      }
    }
    if (!hasKeyword(file.lower())) {
      return List.of();
    }
    try {
      return scan(file);
    } catch (final WorkBudget.BudgetExceededException | StackOverflowError exception) {
      throw new AnalysisFailedException(OVER_BUDGET, exception);
    }
  }

  private boolean skipsFile(final String path) {
    return global.stream().anyMatch(list -> list.skipsFile(path))
        || rule.allowlists().stream().anyMatch(list -> list.skipsFile(path));
  }

  private boolean hasKeyword(final String lower) {
    return rule.keywords().isEmpty() || rule.keywords().stream().anyMatch(lower::contains);
  }

  private List<RuleMatch> scan(final TextFile file) {
    final String text = file.text();
    final WorkBudget budget = new WorkBudget();
    final Matcher matcher = rule.regex().orElseThrow().matcher(new BudgetedText(text, budget));
    final Set<Integer> seen = new HashSet<>();
    final List<RuleMatch> matches = new ArrayList<>();
    int[] lineStarts = null;
    for (final int[] region : SecretRegions.of(file, rule)) {
      budget.reset(region[1] - region[0]);
      matcher.region(region[0], region[1]);
      while (matcher.find()) {
        if (matcher.end() > matcher.start()
            && seen.add(matcher.start())
            && reported(file.path(), text, region, matcher, budget)) {
          lineStarts = lineStarts == null ? SecretText.lineStarts(text) : lineStarts;
          matches.add(new RuleMatch(SecretText.span(lineStarts, matcher), rule.message()));
        }
      }
    }
    return matches;
  }

  private boolean reported(
      final String path,
      final String text,
      final int[] region,
      final Matcher matcher,
      final WorkBudget budget) {
    final String secret = SecretText.secret(matcher, rule.secretGroup());
    if (!exceedsEntropy(secret)) {
      return false;
    }
    final SecretAllowlist.Candidate candidate =
        new SecretAllowlist.Candidate(
            path, secret, matcher.group(), SecretText.line(text, region, matcher.start()));
    return global.stream().noneMatch(list -> list.allows(candidate, budget))
        && rule.allowlists().stream().noneMatch(list -> list.allows(candidate, budget));
  }

  private boolean exceedsEntropy(final String secret) {
    return rule.entropy().isEmpty()
        || rule.entropy().get() == 0
        || Entropy.of(secret) > rule.entropy().get();
  }
}

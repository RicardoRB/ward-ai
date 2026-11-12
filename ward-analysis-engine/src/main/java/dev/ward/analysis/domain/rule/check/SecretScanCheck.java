package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Arrays;
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
    final WorkBudget budget = new WorkBudget(text.length());
    final Matcher matcher = rule.regex().orElseThrow().matcher(new BudgetedText(text, budget));
    final Set<Integer> seen = new HashSet<>();
    final List<RuleMatch> matches = new ArrayList<>();
    int[] lineStarts = null;
    for (final int[] region : SecretRegions.of(file, rule)) {
      matcher.region(region[0], region[1]);
      while (matcher.find()) {
        if (matcher.end() > matcher.start()
            && seen.add(matcher.start())
            && reported(file.path(), text, region, matcher, budget)) {
          lineStarts = lineStarts == null ? lineStarts(text) : lineStarts;
          matches.add(new RuleMatch(span(lineStarts, matcher), rule.message()));
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
    final String secret = secret(matcher);
    if (!exceedsEntropy(secret)) {
      return false;
    }
    final SecretAllowlist.Candidate candidate =
        new SecretAllowlist.Candidate(
            path, secret, matcher.group(), line(text, region, matcher.start()));
    return global.stream().noneMatch(list -> list.allows(candidate, budget))
        && rule.allowlists().stream().noneMatch(list -> list.allows(candidate, budget));
  }

  private boolean exceedsEntropy(final String secret) {
    return rule.entropy().isEmpty()
        || rule.entropy().get() == 0
        || Entropy.of(secret) > rule.entropy().get();
  }

  /** The secret group, else the first group that matched, else the whole match. */
  private String secret(final Matcher matcher) {
    String secret = matcher.group();
    if (rule.secretGroup() > 0) {
      secret = matcher.group(rule.secretGroup());
    } else {
      for (int group = 1; group <= matcher.groupCount(); group++) {
        final String value = matcher.group(group);
        if (value != null && !value.isEmpty()) {
          secret = value;
          break;
        }
      }
    }
    return secret == null ? "" : trimNewlines(secret);
  }

  private static String trimNewlines(final String value) {
    int from = 0;
    int to = value.length();
    while (from < to && value.charAt(from) == '\n') {
      from++;
    }
    while (to > from && value.charAt(to - 1) == '\n') {
      to--;
    }
    return value.substring(from, to);
  }

  private static String line(final String text, final int[] region, final int at) {
    final int start = Math.max(region[0], text.lastIndexOf('\n', at - 1) + 1);
    final int newline = text.indexOf('\n', at);
    final int end = newline < 0 ? region[1] : Math.min(region[1], newline);
    return text.substring(start, Math.max(start, end));
  }

  private static int[] lineStarts(final String text) {
    final List<Integer> starts = new ArrayList<>();
    starts.add(0);
    for (int at = text.indexOf('\n'); at >= 0; at = text.indexOf('\n', at + 1)) {
      starts.add(at + 1);
    }
    return starts.stream().mapToInt(Integer::intValue).toArray();
  }

  private static Span span(final int[] lineStarts, final Matcher matcher) {
    final int[] begin = position(lineStarts, matcher.start());
    final int[] finish = position(lineStarts, matcher.end());
    return new Span(begin[0], begin[1], finish[0], finish[1], matcher.start(), matcher.end());
  }

  /** Returns the 1-based line and column of {@code offset}. */
  private static int[] position(final int[] lineStarts, final int offset) {
    final int found = Arrays.binarySearch(lineStarts, offset);
    final int index = found >= 0 ? found : -found - 2;
    return new int[] {index + 1, offset - lineStarts[index] + 1};
  }
}

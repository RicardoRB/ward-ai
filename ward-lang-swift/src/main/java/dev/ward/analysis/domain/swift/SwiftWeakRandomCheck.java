package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports each call of the C library generators. {@code drand48} and {@code lrand48} are accepted
 * only when the same file seeds them with {@code srand48}, given a constant or a parameter named
 * for the seed: that is a generator for repeatable data, not for secrets (#754). A seed from the
 * clock or from a variable of unknown origin leaves the call reported.
 */
public final class SwiftWeakRandomCheck implements RuleCheck {
  private static final Pattern CALL =
      Pattern.compile("(?<![\\w.])(rand|random|srand|srandom|drand48|lrand48)\\(");
  private static final Pattern SEEDED_CALL = Pattern.compile("drand48|lrand48");
  private static final Pattern SEED =
      Pattern.compile("\\bsrand48\\(\\s*(\\d+|[A-Za-z_]\\w*)\\s*\\)");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftWeakRandomCheck(final String message) {
    this.message = message;
  }

  /**
   * Always fails: the check reads the text of the unit.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the weak-random check needs the text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final SourceUnit unit
  ) {
    final String text = unit.text();
    final boolean seeded = seededForRepeatableData(text);
    final List<RuleMatch> found = new ArrayList<>();
    int offset = 0;
    int number = 1;
    for (final String line : text.split("\n", -1)) {
      final Matcher call = CALL.matcher(line);
      if (call.find() && !(seeded && SEEDED_CALL.matcher(call.group(1)).matches())) {
        final int start = offset + call.start(1);
        final int end = offset + call.end(1);
        found.add(
            new RuleMatch(
                new Span(number, call.start(1) + 1, number, call.end(1) + 1, start, end),
                message
            )
        );
      }
      offset += line.length() + 1;
      number++;
    }
    return List.copyOf(found);
  }

  private static boolean seededForRepeatableData(final String text) {
    final Matcher seed = SEED.matcher(text);
    while (seed.find()) {
      final String argument = seed.group(1);
      if (Character.isDigit(argument.charAt(0)) || isParameter(text, argument)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isParameter(final String text, final String name) {
    return Pattern.compile(
            "(?:init|func\\s+\\w+)\\s*\\([^)]*\\b" + Pattern.quote(name) + "\\s*:"
        )
        .matcher(text)
        .find();
  }
}

package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Reports calls that fail whatever happens at run time, as Error Prone's AlwaysThrows does: a
 * java.time {@code parse}, {@code UUID.fromString} or {@code ByteString.fromHex} of a constant text
 * that does not parse (the text is parsed here with the same JDK methods), an {@code Instant} moved
 * by a {@code Period} of months or years, and {@code ImmutableMap.of} or {@code ImmutableBiMap.of}
 * with a repeated constant key. Chains of {@code ImmutableMap.Builder.put} are not checked. A call
 * whose target or argument constants are unknown is never reported.
 */
public final class JavaAlwaysThrowsCheck implements RuleCheck {

  private static final List<Function<CallSite, Optional<String>>> REASONS =
      List.of(
          AlwaysThrowsParse::time,
          AlwaysThrowsParse::uuid,
          AlwaysThrowsParse::hex,
          AlwaysThrowsAmounts::instant,
          AlwaysThrowsAmounts::duplicates);

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the reason
   */
  public JavaAlwaysThrowsCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return JavaCalls.CALLS_AND_TYPES;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("expression"))) {
      CallSite.of(visit.node(), types)
          .flatMap(JavaAlwaysThrowsCheck::reason)
          .ifPresent(
              reason ->
                  matches.add(
                      CheckSupport.at(
                          visit.node().span(), CheckSupport.fill(message, reason, ""))));
    }
    return List.copyOf(matches);
  }

  private static Optional<String> reason(final CallSite call) {
    return REASONS.stream().map(rule -> rule.apply(call)).flatMap(Optional::stream).findFirst();
  }
}

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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a Joda-Time conversion to the same type, such as {@code DateTime.toDateTime()}, which
 * returns the receiver, as Error Prone's JodaToSelf does. A call whose facts are unknown is never
 * reported.
 */
public final class JavaJodaToSelfCheck implements RuleCheck {

  private static final String JODA = "org.joda.time.";
  private static final Pattern NO_ARGUMENTS = Pattern.compile("\\(\\)$");
  private static final Map<String, String> CONVERSIONS =
      Map.of(
          "DateTime", "toDateTime",
          "Duration", "toDuration",
          "Instant", "toInstant",
          "Interval", "toInterval",
          "Period", "toPeriod");

  private final List<CallMatchCheck> checks;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public JavaJodaToSelfCheck(final String message) {
    Objects.requireNonNull(message, "message");
    final List<CallMatchCheck> built = new ArrayList<>();
    CONVERSIONS.forEach(
        (type, method) ->
            built.add(
                new CallMatchCheck(
                    new CallMatchCheck.Criteria(
                        Set.of(JODA + type),
                        Set.of(method),
                        Set.of(),
                        false,
                        NO_ARGUMENTS,
                        false,
                        Set.of()),
                    message)));
    checks = List.copyOf(built);
  }

  @Override
  public FactNeed needs() {
    return checks.get(0).needs();
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
    return checks.stream()
        .flatMap(check -> check.matches(root, flow, scopes, types, unit).stream())
        .toList();
  }
}

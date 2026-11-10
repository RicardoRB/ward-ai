package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a call of a {@code java.time} {@code from(TemporalAccessor)} factory with an argument
 * whose type always makes it throw, such as {@code LocalDate.from(Instant)}, as Error Prone's
 * FromTemporalAccessor does. A call whose target or argument type is unknown is never reported.
 */
public final class FromTemporalAccessorCheck extends FactsCheck {

  private static final String PACKAGE = "java.time.";
  private static final String INSTANT = PACKAGE + "Instant";
  private static final String LOCAL_DATE = PACKAGE + "LocalDate";
  private static final String LOCAL_TIME = PACKAGE + "LocalTime";
  private static final String LOCAL_DATE_TIME = PACKAGE + "LocalDateTime";
  private static final String OFFSET_TIME = PACKAGE + "OffsetTime";
  private static final String OFFSET_DATE_TIME = PACKAGE + "OffsetDateTime";
  private static final String ZONED_DATE_TIME = PACKAGE + "ZonedDateTime";
  private static final String EXPRESSION = "expression";
  private static final String FROM = "from";
  private static final Set<String> NO_ZONE =
      Set.of(INSTANT, LOCAL_DATE, LOCAL_TIME, LOCAL_DATE_TIME, OFFSET_TIME);
  private static final Map<String, Set<String>> FAILS =
      Map.of(
          INSTANT, Set.of(LOCAL_DATE, LOCAL_TIME, LOCAL_DATE_TIME, OFFSET_TIME),
          LOCAL_DATE, Set.of(INSTANT, LOCAL_TIME, OFFSET_TIME),
          LOCAL_TIME, Set.of(INSTANT, LOCAL_DATE),
          LOCAL_DATE_TIME, Set.of(INSTANT, LOCAL_DATE, LOCAL_TIME, OFFSET_TIME),
          OFFSET_TIME, Set.of(INSTANT, LOCAL_DATE, LOCAL_TIME, LOCAL_DATE_TIME),
          OFFSET_DATE_TIME, NO_ZONE,
          ZONED_DATE_TIME, NO_ZONE);
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL, FactKind.TYPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call; {@code {value}} is the argument type
   */
  public FromTemporalAccessorCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(call -> reportOf(call, types).stream())
        .toList();
  }

  private List<RuleMatch> reportOf(final SyntaxNode call, final UnitTypeFacts types) {
    final List<SyntaxNode> arguments = CallArgumentNodes.all(call);
    final CallTarget target = types.callTarget(call.span()).orElse(null);
    if (target == null
        || !FROM.equals(target.name())
        || !FAILS.containsKey(target.declaringType().name())
        || arguments.size() != 1) {
      return List.of();
    }
    return types
        .typeOf(arguments.get(0).span())
        .filter(given -> given.arrayDepth() == 0)
        .map(TypeRef::name)
        .filter(name -> FAILS.get(target.declaringType().name()).contains(name))
        .map(name -> CheckSupport.at(call.span(), CheckSupport.fill(message, name, "")))
        .stream()
        .toList();
  }
}

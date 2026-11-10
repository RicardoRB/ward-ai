package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a comparison with {@code null} of the result of a getter of a protocol buffer message,
 * which is never null, as Error Prone's ImpossibleNullComparison does. The receiver must be known
 * to implement a protobuf message interface, and the getter must be an instance method that returns
 * a reference type.
 */
public final class JavaProtoNullComparisonCheck implements RuleCheck {

  private static final Set<String> MESSAGES =
      Set.of(
          "com.google.protobuf.MessageLiteOrBuilder",
          "com.google.protobuf.MessageOrBuilder",
          "com.google.protobuf.MessageLite",
          "com.google.protobuf.Message");
  private static final Set<String> COMPARISONS = Set.of("'=='", "'!='");
  private static final int PARTS = 3;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the getter name
   */
  public JavaProtoNullComparisonCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return JavaCalls.WITH_MEMBERS;
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
      getter(visit.node(), types)
          .ifPresent(
              name ->
                  matches.add(
                      CheckSupport.at(visit.node().span(), CheckSupport.fill(message, name, ""))));
    }
    return List.copyOf(matches);
  }

  private static Optional<String> getter(final SyntaxNode comparison, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = comparison.children();
    if (parts.size() != PARTS || !COMPARISONS.contains(parts.get(1).kind())) {
      return Optional.empty();
    }
    final boolean nullOnRight = isNull(parts.get(2));
    final boolean nullOnLeft = isNull(parts.get(0));
    if (nullOnRight == nullOnLeft) {
      return Optional.empty();
    }
    return protoGetter(nullOnRight ? parts.get(0) : parts.get(2), types);
  }

  private static boolean isNull(final SyntaxNode expression) {
    return "null".equals(SyntaxWalk.text(expression));
  }

  private static Optional<String> protoGetter(final SyntaxNode call, final UnitTypeFacts types) {
    final Optional<CallTarget> target = types.callTarget(call.span());
    final boolean getter =
        target.isPresent()
            && !target.get().isStatic()
            && target.get().name().startsWith("get")
            && target.get().returnType() != null
            && !target.get().returnType().primitive();
    final boolean proto =
        JavaCalls.receiver(call)
            .flatMap(receiver -> types.membersOf(receiver.span()))
            .map(
                members ->
                    members.ancestors().stream().map(TypeRef::name).anyMatch(MESSAGES::contains))
            .orElse(false);
    return getter && proto ? target.map(CallTarget::name) : Optional.empty();
  }
}

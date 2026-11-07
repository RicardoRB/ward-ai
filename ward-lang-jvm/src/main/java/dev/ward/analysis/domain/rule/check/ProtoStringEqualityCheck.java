package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports {@code ==} or {@code !=} where one side is a String returned by a getter of a protocol
 * buffer message. Equal strings are distinct objects there, so the comparison must use equals.
 */
public final class ProtoStringEqualityCheck implements RuleCheck {

  private static final String EXPRESSION = "expression";
  private static final int COMPARISON_PARTS = 3;
  private static final String STRING = "java.lang.String";
  private static final Set<String> OPERATORS = Set.of("'=='", "'!='");
  private static final Set<String> MESSAGES =
      Set.of(
          "com.google.protobuf.MessageLiteOrBuilder",
          "com.google.protobuf.MessageOrBuilder",
          "com.google.protobuf.MessageLite",
          "com.google.protobuf.Message",
          "com.google.protobuf.GeneratedMessageLite",
          "com.google.protobuf.GeneratedMessageV3");
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL, FactKind.TYPE, FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the comparison
   */
  public ProtoStringEqualityCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return NEED;
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
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION))) {
      final List<SyntaxNode> parts = visit.node().children();
      if (parts.size() == COMPARISON_PARTS
          && OPERATORS.contains(parts.get(1).kind())
          && (isProtoString(parts.get(0), types) || isProtoString(parts.get(2), types))) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isProtoString(final SyntaxNode operand, final UnitTypeFacts types) {
    final boolean returnsString =
        types
            .callTarget(operand.span())
            .map(CallTarget::returnType)
            .filter(type -> type != null && STRING.equals(type.name()) && type.arrayDepth() == 0)
            .isPresent();
    final List<SyntaxNode> parts = operand.children();
    if (!returnsString || parts.isEmpty() || !EXPRESSION.equals(parts.get(0).kind())) {
      return false;
    }
    final Set<String> known = new HashSet<>();
    types.typeOf(parts.get(0).span()).map(TypeRef::name).ifPresent(known::add);
    types
        .membersOf(parts.get(0).span())
        .map(TypeMembers::ancestors)
        .ifPresent(ancestors -> ancestors.forEach(ancestor -> known.add(ancestor.name())));
    return known.stream().anyMatch(MESSAGES::contains);
  }
}

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
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports {@code out.writeObject(value)} on an object output when the type of the value is a final
 * class with no {@code Serializable} or {@code Externalizable} supertype: the write always fails. A
 * non-final or abstract type may have a serializable subtype, and a call, type or members that the
 * facts do not know are never reported.
 */
public final class NonSerializableWriteCheck implements RuleCheck {
  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";
  private static final String EXPRESSION_LIST = "expressionList";
  private static final String WRITE = "writeObject";
  private static final Set<String> OUTPUTS =
      Set.of("java.io.ObjectOutputStream", "java.io.ObjectOutput");
  private static final Set<String> SERIALIZABLE =
      Set.of("java.io.Serializable", "java.io.Externalizable");
  private static final Set<TypeShape.Kind> CLASSES =
      Set.of(TypeShape.Kind.CLASS, TypeShape.Kind.RECORD);
  private static final FactNeed NEED =
      new FactNeed(
          Set.of(EXPRESSION),
          Set.of(FactKind.CALL, FactKind.TYPE, FactKind.SHAPE, FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call
   */
  public NonSerializableWriteCheck(final String message) {
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
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(
            call ->
                isWrite(call, types)
                    && firstArgument(call).filter(arg -> unserializable(arg, types)).isPresent())
        .map(call -> CheckSupport.at(call.span(), message))
        .toList();
  }

  private static boolean isWrite(final SyntaxNode call, final UnitTypeFacts types) {
    return types
            .callTarget(call.span())
            .map(CallTarget::declaringType)
            .filter(owner -> OUTPUTS.contains(owner.name()))
            .isPresent()
        && types.callTarget(call.span()).filter(target -> WRITE.equals(target.name())).isPresent();
  }

  private static Optional<SyntaxNode> firstArgument(final SyntaxNode call) {
    return call.children().stream()
        .filter(part -> METHOD_CALL.equals(part.kind()))
        .flatMap(invoked -> SyntaxWalk.descendants(invoked).stream())
        .filter(node -> EXPRESSION_LIST.equals(node.kind()))
        .findFirst()
        .flatMap(
            list -> list.children().stream().filter(c -> EXPRESSION.equals(c.kind())).findFirst());
  }

  private static boolean unserializable(final SyntaxNode argument, final UnitTypeFacts types) {
    final boolean finalClass =
        types
            .shapeOf(argument.span())
            .filter(shape -> CLASSES.contains(shape.kind()) && shape.isFinal())
            .isPresent();
    return finalClass
        && types
            .typeOf(argument.span())
            .filter(type -> type.arrayDepth() == 0 && !type.primitive())
            .isPresent()
        && types
            .membersOf(argument.span())
            .map(TypeMembers::ancestors)
            .filter(ancestors -> ancestors.stream().noneMatch(a -> SERIALIZABLE.contains(a.name())))
            .isPresent();
  }
}

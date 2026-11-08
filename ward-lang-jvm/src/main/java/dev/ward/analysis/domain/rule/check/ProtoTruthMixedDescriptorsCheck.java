package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports a call of {@code ignoringFields} on a ProtoTruth assertion whose arguments name fields of
 * more than one protocol buffer, or of one that is not the subject of the {@code assertThat} at the
 * start of the chain, as Error Prone's ProtoTruthMixedDescriptors does. A field is an argument
 * written {@code Message.FIELD_NUMBER}, whose qualifier is a message type. The subject is judged
 * when it is a generated message and not an {@code Iterable}; anything unknown reports nothing.
 */
public final class ProtoTruthMixedDescriptorsCheck extends FactsCheck {

  private static final String TRUTH = "com.google.common.truth.extensions.proto.";
  private static final Set<String> ASSERTIONS =
      Set.of(TRUTH + "ProtoFluentAssertion", TRUTH + "ProtoSubject");
  private static final String PROTO_TRUTH = TRUTH + "ProtoTruth";
  private static final String MESSAGE = "com.google.protobuf.Message";
  private static final Set<String> GENERATED =
      Set.of(
          "com.google.protobuf.GeneratedMessage",
          "com.google.protobuf.GeneratedMessageV3",
          "com.google.protobuf.GeneratedMessageLite");
  private static final String EXPRESSION = "expression";
  private static final int SINGLE = 1;
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL, FactKind.TYPE, FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call
   */
  public ProtoTruthMixedDescriptorsCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(call -> SyntaxWalk.text(call).endsWith(")"))
        .filter(call -> mixed(call, types))
        .map(call -> CheckSupport.at(call.span(), message))
        .toList();
  }

  private static boolean mixed(final SyntaxNode call, final UnitTypeFacts types) {
    final boolean ignoring =
        types
            .callTarget(call.span())
            .filter(target -> "ignoringFields".equals(target.name()))
            .filter(target -> ASSERTIONS.contains(target.declaringType().name()))
            .isPresent();
    if (!ignoring) {
      return false;
    }
    final Set<String> owners =
        CallArgumentNodes.all(call).stream()
            .map(argument -> owner(argument, types))
            .flatMap(Optional::stream)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    return owners.size() > 1
        || owners.size() == SINGLE && subjectDiffers(call, owners.iterator().next(), types);
  }

  /** The message type that owns the field an argument names, if the argument is {@code Q.NAME}. */
  private static Optional<String> owner(final SyntaxNode argument, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = argument.children();
    if (parts.isEmpty() || !EXPRESSION.equals(parts.get(0).kind())) {
      return Optional.empty();
    }
    final SyntaxNode qualifier = parts.get(0);
    return types
        .typeOf(qualifier.span())
        .filter(
            type ->
                types.membersOf(qualifier.span()).map(TypeMembers::ancestors).stream()
                    .flatMap(List::stream)
                    .anyMatch(ancestor -> MESSAGE.equals(ancestor.name())))
        .map(TypeRef::name);
  }

  private static boolean subjectDiffers(
      final SyntaxNode call, final String owner, final UnitTypeFacts types) {
    SyntaxNode node = call;
    while (!node.children().isEmpty() && EXPRESSION.equals(node.children().get(0).kind())) {
      node = node.children().get(0);
      final Optional<CallTarget> target = types.callTarget(node.span());
      final List<SyntaxNode> arguments = CallArgumentNodes.all(node);
      if (target.filter(ProtoTruthMixedDescriptorsCheck::isAssertThat).isPresent()
          && arguments.size() == 1) {
        return generatedOtherThan(arguments.get(0), owner, types);
      }
    }
    return false;
  }

  private static boolean isAssertThat(final CallTarget target) {
    return target.isStatic()
        && "assertThat".equals(target.name())
        && PROTO_TRUTH.equals(target.declaringType().name());
  }

  private static boolean generatedOtherThan(
      final SyntaxNode subject, final String owner, final UnitTypeFacts types) {
    return types
        .typeOf(subject.span())
        .filter(type -> type.arrayDepth() == 0 && !owner.equals(type.name()))
        .filter(
            type ->
                types.membersOf(subject.span()).map(TypeMembers::ancestors).stream()
                    .flatMap(List::stream)
                    .anyMatch(ancestor -> GENERATED.contains(ancestor.name())))
        .isPresent();
  }
}

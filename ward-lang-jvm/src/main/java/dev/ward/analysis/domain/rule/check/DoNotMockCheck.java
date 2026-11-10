package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reports a mock of a type annotated {@code @DoNotMock}, as Error Prone's DoNotMock does: a call of
 * {@code Mockito.mock} or {@code Mockito.spy} with a class literal, or a field annotated
 * {@code @Mock} or {@code @Spy}. A type whose annotations are unknown is never reported.
 */
public final class DoNotMockCheck extends FactsCheck {

  private static final String DO_NOT_MOCK = ".DoNotMock";
  private static final String MOCKITO = "org.mockito.Mockito";
  private static final String TYPE_TYPE = "typeType";
  private static final String EXPRESSION = "expression";
  private static final String MEMBER = "classBodyDeclaration";
  private static final Set<String> FACTORIES = Set.of("mock", "spy");
  private static final List<String> ANNOTATIONS =
      List.of("@Mock", "@Spy", "@org.mockito.Mock", "@org.mockito.Spy");
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION, MEMBER), Set.of(FactKind.CALL, FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the mock
   */
  public DoNotMockCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> calls =
        SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
            .map(SyntaxWalk.Visit::node)
            .filter(call -> mocksCall(call, types))
            .map(call -> CheckSupport.at(call.span(), message))
            .toList();
    final List<RuleMatch> fields =
        SyntaxWalk.visits(root, Set.of(MEMBER)).stream()
            .map(SyntaxWalk.Visit::node)
            .filter(member -> mocksField(member, types))
            .map(member -> CheckSupport.at(member.span(), message))
            .toList();
    return Stream.concat(calls.stream(), fields.stream()).toList();
  }

  private static boolean mocksCall(final SyntaxNode call, final UnitTypeFacts types) {
    final List<SyntaxNode> arguments = CallArgumentNodes.all(call);
    return !arguments.isEmpty()
        && SyntaxWalk.text(arguments.get(0)).endsWith(".class")
        && types
            .callTarget(call.span())
            .filter(
                target ->
                    MOCKITO.equals(target.declaringType().name())
                        && FACTORIES.contains(target.name()))
            .isPresent()
        && doNotMock(arguments.get(0), types);
  }

  private static boolean mocksField(final SyntaxNode member, final UnitTypeFacts types) {
    final String text = SyntaxWalk.text(member);
    return ANNOTATIONS.stream().anyMatch(text::startsWith)
        && SyntaxWalk.descendants(member).stream()
            .anyMatch(node -> "fieldDeclaration".equals(node.kind()))
        && doNotMock(member, types);
  }

  private static boolean doNotMock(final SyntaxNode node, final UnitTypeFacts types) {
    return SyntaxWalk.descendants(node).stream()
        .filter(child -> TYPE_TYPE.equals(child.kind()))
        .findFirst()
        .flatMap(type -> types.membersOf(type.span()))
        .map(TypeMembers::annotations)
        .filter(found -> found.stream().anyMatch(name -> name.endsWith(DO_NOT_MOCK)))
        .isPresent();
  }
}

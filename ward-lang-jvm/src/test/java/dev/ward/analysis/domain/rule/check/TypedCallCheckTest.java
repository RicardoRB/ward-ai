package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TypedCallCheckTest {

  private static final String GET_INT = "getInt";
  private static final int RECEIVER = 1;
  private static final int ARGUMENT = 5;

  /** {@code receiver.name(argument)} with the receiver at column 1 and the argument at 5. */
  private static SyntaxNode call(final String receiver, final String name, final String argument) {
    final SyntaxNode list =
        CorrectnessTrees.node(
            "expressionList", ARGUMENT, CorrectnessTrees.expression(argument, ARGUMENT));
    final SyntaxNode invoked =
        CorrectnessTrees.node(
            "methodCall",
            3,
            CorrectnessTrees.node("identifier", 3, CorrectnessTrees.leaf("IDENTIFIER", name, 3)),
            CorrectnessTrees.node("arguments", 4, list));
    return CorrectnessTrees.node(
        "expression",
        1,
        CorrectnessTrees.expression(receiver, RECEIVER),
        CorrectnessTrees.leaf(".", ".", 2),
        invoked);
  }

  private static SyntaxNode noArguments(final String receiver, final String name) {
    final SyntaxNode invoked =
        CorrectnessTrees.node(
            "methodCall",
            3,
            CorrectnessTrees.node("identifier", 3, CorrectnessTrees.leaf("IDENTIFIER", name, 3)),
            CorrectnessTrees.node("arguments", 4));
    return CorrectnessTrees.node(
        "expression",
        1,
        CorrectnessTrees.expression(receiver, RECEIVER),
        CorrectnessTrees.leaf(".", ".", 2),
        invoked);
  }

  private static TypedCallCheck check(
      final Optional<Pattern> receiverText,
      final Optional<Pattern> receiverType,
      final Set<String> argumentTypes,
      final Optional<Pattern> argumentText) {
    return new TypedCallCheck(
        Pattern.compile("get\\w+"),
        new TypedCallCheck.Receiver(receiverText, receiverType),
        new TypedCallCheck.Argument(0, argumentTypes, argumentText),
        "message");
  }

  private static List<RuleMatch> run(
      final TypedCallCheck check, final SyntaxNode root, final UnitTypeFacts types) {
    return check.matches(root, FlowView.NONE, ScopeView.NONE, types, Calls.UNIT);
  }

  @Test
  void aCallWithOnlyANameConditionIsReportedWhateverItsArguments() {
    final TypedCallCheck check =
        check(Optional.empty(), Optional.empty(), Set.of(), Optional.empty());

    assertThat(check.needs().facts()).containsExactly(FactKind.TYPE);
    assertThat(check.matches(call("rs", GET_INT, "0"))).isEmpty();
    assertThat(run(check, call("rs", GET_INT, "0"), UnitTypeFacts.none())).hasSize(1);
    assertThat(run(check, noArguments("rs", GET_INT), UnitTypeFacts.none())).hasSize(1);
    assertThat(run(check, call("rs", "close", "0"), UnitTypeFacts.none())).isEmpty();
  }

  @Test
  void anExpressionThatIsNotACallIsSkipped() {
    final TypedCallCheck check =
        check(Optional.empty(), Optional.empty(), Set.of(), Optional.empty());

    assertThat(run(check, CorrectnessTrees.expression(GET_INT, 1), UnitTypeFacts.none())).isEmpty();
    assertThat(
            run(
                check,
                CorrectnessTrees.node("expression", 1, CorrectnessTrees.leaf("X", "a", 1)),
                UnitTypeFacts.none()))
        .isEmpty();
  }

  @Test
  void theReceiverTextMustMatch() {
    final TypedCallCheck check =
        check(Optional.of(Pattern.compile("Math")), Optional.empty(), Set.of(), Optional.empty());

    assertThat(run(check, call("Math", GET_INT, "0"), UnitTypeFacts.none())).hasSize(1);
    assertThat(run(check, call("Other", GET_INT, "0"), UnitTypeFacts.none())).isEmpty();
  }

  @Test
  void theReceiverTypeMustMatchAndBeKnown() {
    final TypedCallCheck check =
        check(Optional.empty(), Optional.of(Pattern.compile("a\\.B")), Set.of(), Optional.empty());

    assertThat(
            run(
                check,
                call("rs", GET_INT, "0"),
                CorrectnessTrees.table(
                    RECEIVER, CorrectnessTrees.facts(CorrectnessTrees.type("a.B", false)))))
        .hasSize(1);
    assertThat(
            run(
                check,
                call("rs", GET_INT, "0"),
                CorrectnessTrees.table(
                    RECEIVER, CorrectnessTrees.facts(CorrectnessTrees.type("c.D", false)))))
        .isEmpty();
    assertThat(run(check, call("rs", GET_INT, "0"), UnitTypeFacts.none())).isEmpty();
  }

  @Test
  void theArgumentTypeAndTextMustFit() {
    final TypedCallCheck typed =
        check(Optional.empty(), Optional.empty(), Set.of("int"), Optional.empty());
    final TypedCallCheck text =
        check(Optional.empty(), Optional.empty(), Set.of(), Optional.of(Pattern.compile("0")));

    assertThat(
            run(
                typed,
                call("rs", GET_INT, "x"),
                CorrectnessTrees.table(
                    ARGUMENT, CorrectnessTrees.facts(CorrectnessTrees.type("int", true)))))
        .hasSize(1);
    assertThat(
            run(
                typed,
                call("rs", GET_INT, "x"),
                CorrectnessTrees.table(
                    ARGUMENT, CorrectnessTrees.facts(CorrectnessTrees.type("long", true)))))
        .isEmpty();
    assertThat(run(typed, call("rs", GET_INT, "x"), UnitTypeFacts.none())).isEmpty();
    assertThat(run(typed, noArguments("rs", GET_INT), UnitTypeFacts.none())).isEmpty();
    assertThat(run(text, call("rs", GET_INT, "0"), UnitTypeFacts.none())).hasSize(1);
    assertThat(run(text, call("rs", GET_INT, "1"), UnitTypeFacts.none())).isEmpty();
  }
}

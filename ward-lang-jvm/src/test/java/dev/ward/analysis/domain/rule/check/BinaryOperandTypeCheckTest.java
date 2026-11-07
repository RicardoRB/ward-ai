package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BinaryOperandTypeCheckTest {

  private static final int RIGHT = 3;
  private static final UnitTypeFacts BYTE =
      CorrectnessTrees.table(1, CorrectnessTrees.facts(CorrectnessTrees.type("byte", true)));
  private static final UnitTypeFacts BOTH =
      CorrectnessTrees.table(
          1,
          CorrectnessTrees.facts(CorrectnessTrees.type("java.lang.Integer", false)),
          RIGHT,
          CorrectnessTrees.facts(CorrectnessTrees.type("java.lang.Integer", false)));

  private static BinaryOperandTypeCheck check(
      final Set<String> operators,
      final Set<String> types,
      final boolean both,
      final String min,
      final String max) {
    return new BinaryOperandTypeCheck(
        operators,
        types,
        both,
        new BinaryOperandTypeCheck.Range(
            Optional.ofNullable(min).map(BigInteger::new),
            Optional.ofNullable(max).map(BigInteger::new)),
        "message");
  }

  private static SyntaxNode binary(final String operator, final String right) {
    return CorrectnessTrees.node(
        "expression",
        1,
        CorrectnessTrees.expression("b", 1),
        CorrectnessTrees.leaf("OP", operator, 2),
        CorrectnessTrees.expression(right, RIGHT));
  }

  private static List<RuleMatch> run(
      final BinaryOperandTypeCheck check, final SyntaxNode root, final UnitTypeFacts types) {
    return check.matches(root, FlowView.NONE, ScopeView.NONE, types, Calls.UNIT);
  }

  @Test
  void declaresWhatItReadsAndNeedsFactsToReport() {
    final BinaryOperandTypeCheck check = check(Set.of("|"), Set.of("byte"), false, null, null);

    assertThat(check.needs().facts()).containsExactly(FactKind.TYPE);
    assertThat(check.matches(binary("|", "1"))).isEmpty();
    assertThat(run(check, binary("|", "1"), UnitTypeFacts.none())).isEmpty();
  }

  @Test
  void anOperatorAndLeftTypeFromTheSetAreReported() {
    final BinaryOperandTypeCheck check = check(Set.of("|"), Set.of("byte"), false, null, null);

    assertThat(run(check, binary("|", "1"), BYTE)).hasSize(1);
    assertThat(run(check, binary("&", "1"), BYTE)).isEmpty();
    assertThat(
            run(
                check,
                binary("|", "1"),
                CorrectnessTrees.table(
                    1, CorrectnessTrees.facts(CorrectnessTrees.type("int", true)))))
        .isEmpty();
  }

  @Test
  void anExpressionThatIsNotBinaryIsSkipped() {
    final BinaryOperandTypeCheck check = check(Set.of("|"), Set.of("byte"), false, null, null);
    final SyntaxNode notBinary =
        CorrectnessTrees.node(
            "expression",
            1,
            CorrectnessTrees.expression("b", 1),
            CorrectnessTrees.leaf("OP", "|", 2));
    final SyntaxNode token =
        CorrectnessTrees.node(
            "expression",
            1,
            CorrectnessTrees.leaf("X", "a", 1),
            CorrectnessTrees.leaf("OP", "|", 2),
            CorrectnessTrees.leaf("X", "c", RIGHT));

    assertThat(run(check, notBinary, BYTE)).isEmpty();
    assertThat(run(check, token, BYTE)).isEmpty();
  }

  @Test
  void twoTokenOperatorsAreJoined() {
    final BinaryOperandTypeCheck check = check(Set.of(">>"), Set.of("byte"), false, null, null);
    final SyntaxNode shift =
        CorrectnessTrees.node(
            "expression",
            1,
            CorrectnessTrees.expression("b", 1),
            CorrectnessTrees.leaf("GT", ">", 2),
            CorrectnessTrees.leaf("GT", ">", RIGHT),
            CorrectnessTrees.expression("1", 4));

    assertThat(run(check, shift, BYTE)).hasSize(1);
  }

  @Test
  void bothSidesMustHaveATypeFromTheSetWhenAsked() {
    final BinaryOperandTypeCheck check =
        check(Set.of("=="), Set.of("java.lang.Integer"), true, null, null);

    assertThat(run(check, binary("==", "c"), BOTH)).hasSize(1);
    assertThat(
            run(
                check,
                binary("==", "c"),
                CorrectnessTrees.table(
                    1, CorrectnessTrees.facts(CorrectnessTrees.type("java.lang.Integer", false)))))
        .isEmpty();
  }

  @Test
  void aLiteralOutsideTheRangeIsReportedAndOneInsideIsNot() {
    final BinaryOperandTypeCheck check = check(Set.of("=="), Set.of("byte"), false, "-128", "127");

    assertThat(run(check, binary("==", "200"), BYTE)).hasSize(1);
    assertThat(run(check, binary("==", "0xFF"), BYTE)).hasSize(1);
    assertThat(run(check, binary("==", "-129"), BYTE)).hasSize(1);
    assertThat(run(check, binary("==", "1_000L"), BYTE)).hasSize(1);
    assertThat(run(check, binary("==", "100"), BYTE)).isEmpty();
    assertThat(run(check, binary("==", "-128"), BYTE)).isEmpty();
  }

  @Test
  void aRightOperandThatIsNotAnIntegerLiteralIsNotReportedWhenARangeIsSet() {
    final BinaryOperandTypeCheck check = check(Set.of("=="), Set.of("byte"), false, "-128", "127");

    assertThat(run(check, binary("==", "other"), BYTE)).isEmpty();
    assertThat(run(check, binary("==", ""), BYTE)).isEmpty();
    assertThat(run(check, binary("==", "0x"), BYTE)).isEmpty();
  }
}

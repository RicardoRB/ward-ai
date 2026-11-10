package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IgnoredResultCheckTest {

  private static final String STRING = "java.lang.String";
  private static final IgnoredResultCheck CHECK = new IgnoredResultCheck(Set.of(STRING), "message");

  private static SyntaxNode statement() {
    return CorrectnessTrees.node(
        "statement", 1, CorrectnessTrees.expression("s", 1), CorrectnessTrees.leaf("';'", ";", 2));
  }

  private static List<RuleMatch> run(final SyntaxNode root, final UnitTypeFacts types) {
    return CHECK.matches(root, FlowView.NONE, ScopeView.NONE, types, Calls.UNIT);
  }

  @Test
  void aDroppedResultOfAListedTypeIsReported() {
    assertThat(CHECK.needs().facts()).containsExactly(FactKind.CALL);
    assertThat(CHECK.matches(statement())).isEmpty();
    assertThat(run(statement(), CorrectnessTrees.table(1, CorrectnessTrees.call(STRING, STRING))))
        .hasSize(1);
  }

  @Test
  void aVoidResultAnotherTypeOrAnUnknownCallIsNotReported() {
    assertThat(run(statement(), CorrectnessTrees.table(1, CorrectnessTrees.call(STRING, "void"))))
        .isEmpty();
    assertThat(
            run(
                statement(),
                CorrectnessTrees.table(1, CorrectnessTrees.call("java.util.List", STRING))))
        .isEmpty();
    assertThat(run(statement(), UnitTypeFacts.none())).isEmpty();
  }

  @Test
  void aStatementThatIsNotAnExpressionStatementIsSkipped() {
    final SyntaxNode returning =
        CorrectnessTrees.node(
            "statement",
            1,
            CorrectnessTrees.leaf("'return'", "return", 1),
            CorrectnessTrees.expression("s", 1),
            CorrectnessTrees.leaf("';'", ";", 2));
    final SyntaxNode empty =
        CorrectnessTrees.node(
            "statement",
            1,
            CorrectnessTrees.leaf("';'", ";", 1),
            CorrectnessTrees.leaf("';'", ";", 2));

    assertThat(run(returning, CorrectnessTrees.table(1, CorrectnessTrees.call(STRING, STRING))))
        .isEmpty();
    assertThat(run(empty, CorrectnessTrees.table(1, CorrectnessTrees.call(STRING, STRING))))
        .isEmpty();
  }
}

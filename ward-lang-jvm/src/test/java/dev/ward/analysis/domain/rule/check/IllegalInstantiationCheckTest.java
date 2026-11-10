package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The illegal-instantiation check on {@code new} expressions and their listed classes. */
class IllegalInstantiationCheckTest extends TreeBuilders {

  private static final String MESSAGE = "instantiates {value}";

  @Test
  void newOfAListedClassIsReportedWithoutItsArguments() {
    final SyntaxNode creation = newOf("Date()");
    final List<dev.ward.analysis.domain.rule.RuleMatch> found =
        new IllegalInstantiationCheck(Set.of("Date"), MESSAGE).check(unit(creation), FILE);
    assertThat(found).hasSize(1);
    assertThat(found.get(0).message()).isEqualTo("instantiates Date");
  }

  @Test
  void newOfAnotherClassOrAnEmptyListIsNotReported() {
    assertThat(
            new IllegalInstantiationCheck(Set.of("Date"), MESSAGE)
                .check(unit(newOf("Object()")), FILE))
        .isEmpty();
    assertThat(new IllegalInstantiationCheck(Set.of(), MESSAGE).check(unit(newOf("Date()")), FILE))
        .isEmpty();
  }

  private static SyntaxNode newOf(final String creator) {
    return n(EXPRESSION, t("'new'", "new"), n("creator", t(TOKEN_IDENTIFIER, creator)));
  }
}

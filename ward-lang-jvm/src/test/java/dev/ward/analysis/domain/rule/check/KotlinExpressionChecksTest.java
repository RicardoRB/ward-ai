package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the ktlint checks about declarations and expressions over real Kotlin sources. */
class KotlinExpressionChecksTest {

  private static final String MESSAGE = "m";

  private static List<RuleMatch> matchesOf(final RuleCheck check, final String source) {
    return check.matches(KotlinSourceTrees.parse(source));
  }

  @Test
  void backingPropertyNeedsPrivateAndAPublicCorrelate() {
    final KotlinBackingPropertyNamingCheck check = new KotlinBackingPropertyNamingCheck(MESSAGE);

    assertThat(matchesOf(check, "class A {\n  private val _x = 1\n  val x get() = _x\n}\n"))
        .isEmpty();
    assertThat(matchesOf(check, "class A {\n  private val _x = 1\n}\n")).hasSize(1);
    assertThat(matchesOf(check, "class A {\n  val _x = 1\n  val x get() = _x\n}\n")).hasSize(1);
    assertThat(matchesOf(check, "class A {\n  private val _x = 1\n  private val x get() = _x\n}\n"))
        .hasSize(1);
  }

  @Test
  void backingPropertyMayBeCorrelatedWithAPublicGetter() {
    final KotlinBackingPropertyNamingCheck check = new KotlinBackingPropertyNamingCheck(MESSAGE);

    assertThat(matchesOf(check, "class A {\n  private val _x = 1\n  fun getX() = _x\n}\n"))
        .isEmpty();
    assertThat(matchesOf(check, "class A {\n  private val _x = 1\n  fun getX(a: Int) = _x\n}\n"))
        .hasSize(1);
  }

  @Test
  void backingPropertyNameMustBeLowerCamelCaseAndNotOverride() {
    final KotlinBackingPropertyNamingCheck check = new KotlinBackingPropertyNamingCheck(MESSAGE);

    assertThat(matchesOf(check, "class A {\n  private val _X = 1\n  val X get() = _X\n}\n"))
        .hasSize(1);
    assertThat(matchesOf(check, "class A {\n  open val _ = 1\n}\n")).isEmpty();
    assertThat(matchesOf(check, "open class A {\n  override val _x = 1\n}\n")).isEmpty();
  }

  @Test
  void functionBodyThatOnlyReturnsOrThrowsIsReported() {
    final KotlinFunctionExpressionBodyCheck check = new KotlinFunctionExpressionBodyCheck(MESSAGE);

    assertThat(matchesOf(check, "fun f(): Int {\n  return 1\n}\n")).hasSize(1);
    assertThat(matchesOf(check, "fun f(): Int {\n  throw Error()\n}\n")).hasSize(1);
    assertThat(matchesOf(check, "fun f() {\n  return\n}\n")).isEmpty();
    assertThat(matchesOf(check, "fun f(): Int {\n  val a = 1\n  return a\n}\n")).isEmpty();
    assertThat(matchesOf(check, "fun f(): Int = 1\n")).isEmpty();
  }

  @Test
  void unitReturnTypeOnABlockBodyIsReported() {
    final KotlinUnitReturnCheck check = new KotlinUnitReturnCheck(MESSAGE);

    assertThat(matchesOf(check, "fun f(): Unit {\n}\n")).hasSize(1);
    assertThat(matchesOf(check, "fun f(): Unit = Unit\n")).isEmpty();
    assertThat(matchesOf(check, "fun f(): Int {\n  return 1\n}\n")).isEmpty();
  }

  @Test
  void templateBracesAndToStringCallsAreReported() {
    final KotlinStringTemplateCheck check = new KotlinStringTemplateCheck(MESSAGE);

    assertThat(matchesOf(check, "val s = \"${x}\"\n")).hasSize(1);
    assertThat(matchesOf(check, "val s = \"${x}!\"\n")).hasSize(1);
    assertThat(matchesOf(check, "val s = \"${x}y\"\n")).isEmpty();
    assertThat(matchesOf(check, "val s = \"${x.toString()}\"\n")).hasSize(1);
    assertThat(matchesOf(check, "val s = \"${x.length}\"\n")).isEmpty();
    assertThat(matchesOf(check, "val s = \"${1}\"\n")).isEmpty();
    assertThat(matchesOf(check, "val s = \"$x\"\n")).isEmpty();
  }

  @Test
  void emptyParenthesesBeforeTrailingLambdaAreReported() {
    final KotlinTrailingLambdaParenthesesCheck check =
        new KotlinTrailingLambdaParenthesesCheck(MESSAGE);

    assertThat(matchesOf(check, "fun u() {\n  run() { }\n}\n")).hasSize(1);
    assertThat(matchesOf(check, "fun u() {\n  run { }\n}\n")).isEmpty();
    assertThat(matchesOf(check, "fun u() {\n  run(1) { }\n}\n")).isEmpty();
  }
}

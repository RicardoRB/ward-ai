package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Edge cases of the Pylint-derived custom checks, written inline so each reads with its source. */
final class PylintCustomChecksTest {

  private static final Map<String, RuleDefinition> RULES =
      CatalogRuleDefinitions.all().stream()
          .collect(Collectors.toMap(RuleDefinition::id, Function.identity()));
  private static final RuleFixtureRunner RUNNER = new RuleFixtureRunner();

  /** The number of findings the rule {@code id} reports on {@code source}. */
  private static int count(final String id, final String source) {
    return RUNNER
        .run(
            RULES.get("ward:python/" + id),
            "module.py",
            source,
            FixtureTypeFacts.of(Optional.empty()))
        .size();
  }

  @Test
  void elifAfterReturningBranchIsReported() {
    assertThat(
            count(
                "no-else-return",
                "def f(x):\n    if x:\n        return 1\n    elif x > 2:\n        return 2\n    return 3\n"))
        .isEqualTo(1);
  }

  @Test
  void stringAfterAssignmentInAFunctionIsReported() {
    assertThat(
            count(
                "pointless-string-statement",
                "def f():\n    x = 1\n    \"after the assignment\"\n    return x\n"))
        .isEqualTo(1);
  }

  @Test
  void stringAfterAssignmentInInitIsAttributeDocstring() {
    assertThat(
            count(
                "pointless-string-statement",
                "class A:\n    def __init__(self):\n        self.x = 1\n        \"attribute\"\n"))
        .isZero();
  }

  @Test
  void logUnsupportedFormatNeedsArguments() {
    assertThat(count("logging-unsupported-format", "import logging\nlogging.info(\"%y\")\n"))
        .isZero();
    assertThat(count("logging-unsupported-format", "import logging\nlogging.info(\"%y\", 1)\n"))
        .isEqualTo(1);
  }

  @Test
  void literalFormatIndexOutOfRangeIsReportedButNamesAreNot() {
    assertThat(count("invalid-format-index", "x = \"{0[5]}\".format(xs)\n")).isZero();
    assertThat(count("invalid-format-index", "x = \"{0[5]}\".format([1, 2])\n")).isEqualTo(1);
  }

  @Test
  void fieldInsideMakeDataclassIsValid() {
    assertThat(
            count(
                "invalid-field-call",
                "from dataclasses import field, make_dataclass\n"
                    + "X = make_dataclass(\"X\", [(\"a\", int, field(default=1))])\n"))
        .isZero();
  }

  @Test
  void keywordOnlyParameterWithDefaultIsNotMissing() {
    assertThat(count("missing-kwoa", "def f(a, *, b=2):\n    return a\n\n\nf(1)\n")).isZero();
  }

  @Test
  void nonlocalBoundByAParameterOfTheEnclosingFunctionIsBound() {
    assertThat(
            count(
                "nonlocal-without-binding",
                "def f(y):\n    def g():\n        nonlocal y\n        return y\n    return g\n"))
        .isZero();
  }

  @Test
  void enumExtensionOfAnEnumWithoutMembersIsValid() {
    assertThat(
            count(
                "invalid-enum-extension",
                "from enum import Enum\n\n\nclass C(Enum):\n    def m(self):\n        x = 1\n\n\n"
                    + "class D(C):\n    pass\n"))
        .isZero();
  }

  @Test
  void dictModifiedWhileIteratedIsReportedOnlyForLiteralDict() {
    assertThat(count("modified-iterating-dict", "d = {\"a\": 1}\nfor k in d:\n    del d[k]\n"))
        .isEqualTo(1);
    assertThat(count("modified-iterating-dict", "d = load()\nfor k in d:\n    del d[k]\n"))
        .isZero();
  }
}

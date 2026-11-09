package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The same-module call checks, parsed from real Python source. */
final class CallSignatureChecksTest {

  private static final String MESSAGE = "m";

  @Test
  void tooManyPositionalReportsAMoreArgumentsThanTheFunctionTakes() {
    final String source = "def f(a, b):\n    return a\n\n\nf(1, 2, 3)\nf(1, 2)\n";

    assertThat(run(CallSignatureCheck.Problem.TOO_MANY_POSITIONAL, source)).hasSize(1);
  }

  @Test
  void varargsAcceptAnyPositionalCount() {
    final String source = "def f(a, *rest):\n    return a\n\n\nf(1, 2, 3)\n";

    assertThat(run(CallSignatureCheck.Problem.TOO_MANY_POSITIONAL, source)).isEmpty();
  }

  @Test
  void unexpectedKeywordIsReportedUnlessTheSignatureHasKwargs() {
    assertThat(
            run(
                CallSignatureCheck.Problem.UNEXPECTED_KEYWORD,
                "def f(a):\n    return a\n\n\nf(c=1)\n"))
        .hasSize(1);
    assertThat(
            run(
                CallSignatureCheck.Problem.UNEXPECTED_KEYWORD,
                "def f(a, **kw):\n    return a\n\n\nf(c=1)\n"))
        .isEmpty();
  }

  @Test
  void redundantKeywordIsReportedForAParameterAPositionalArgumentFills() {
    assertThat(
            run(
                CallSignatureCheck.Problem.REDUNDANT_KEYWORD,
                "def f(a, b):\n    return a\n\n\nf(1, a=2)\n"))
        .hasSize(1);
    assertThat(
            run(
                CallSignatureCheck.Problem.REDUNDANT_KEYWORD,
                "def f(a, b):\n    return a\n\n\nf(1, b=2)\n"))
        .isEmpty();
  }

  @Test
  void unpackedArgumentsAndRepeatedNamesAreLeftAlone() {
    assertThat(
            run(
                CallSignatureCheck.Problem.TOO_MANY_POSITIONAL,
                "def f(a):\n    return a\n\n\nf(*[1, 2])\n"))
        .isEmpty();
    assertThat(
            run(
                CallSignatureCheck.Problem.TOO_MANY_POSITIONAL,
                "def f(a):\n    return a\n\n\ndef f(a, b):\n    return a\n\n\nf(1, 2)\n"))
        .isEmpty();
  }

  @Test
  void aClassIsCheckedThroughItsInitWithoutSelf() {
    final String source =
        "class C:\n    def __init__(self, a):\n        self.a = a\n\n\nC(1, 2)\nC(1)\n";

    assertThat(run(CallSignatureCheck.Problem.TOO_MANY_POSITIONAL, source)).hasSize(1);
  }

  @Test
  void aClassWithBasesIsNotChecked() {
    final String source =
        "class B:\n    pass\n\n\nclass C(B):\n    def __init__(self, a):\n        self.a = a\n\n\nC(1, 2)\n";

    assertThat(run(CallSignatureCheck.Problem.TOO_MANY_POSITIONAL, source)).isEmpty();
  }

  @Test
  void repeatedKeywordReportsAKeywordAlsoKeyOfAnUnpackedLiteralDict() {
    assertThat(
            new RepeatedKeywordCheck(MESSAGE)
                .matches(tree("def f(name):\n    return name\n\n\nf(name=1, **{\"name\": 2})\n")))
        .hasSize(1);
    assertThat(
            new RepeatedKeywordCheck(MESSAGE)
                .matches(tree("def f(name):\n    return name\n\n\nf(name=1, **{\"other\": 2})\n")))
        .isEmpty();
  }

  private static List<RuleMatch> run(
      final CallSignatureCheck.Problem problem, final String source) {
    return new CallSignatureCheck(problem, MESSAGE).matches(tree(source));
  }

  private static SyntaxNode tree(final String source) {
    return new AntlrLanguageFrontend()
        .parse(new SourceUnit("module.py", AnalysisLanguage.PYTHON, source))
        .tree();
  }
}

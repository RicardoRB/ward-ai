package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.Confidence;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.python.PythonControlFlow;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;

/** Runs the weak digest check over a source text. */
final class WeakDigestRuns {
  private WeakDigestRuns() {}

  static List<RuleMatch> run(final String source) {
    final SourceUnit unit = new SourceUnit("m.py", AnalysisLanguage.PYTHON, source);
    final SyntaxNode tree = new AntlrLanguageFrontend().parse(unit).tree();
    final FlowView flow = new FlowView(new PythonControlFlow().lower(unit, tree).graphs());
    return new WeakDigestCheck("weak", Confidence.POSSIBLE).matches(tree, flow);
  }

  static void assertConfidence(final String source, final Confidence expected) {
    assertThat(run("import hashlib\nimport hmac\nimport os\n" + source))
        .extracting(RuleMatch::confidence)
        .containsExactly(expected);
  }

  static void assertNone(final String source) {
    assertThat(run("import hashlib\nimport hmac\nimport os\n" + source)).isEmpty();
  }
}

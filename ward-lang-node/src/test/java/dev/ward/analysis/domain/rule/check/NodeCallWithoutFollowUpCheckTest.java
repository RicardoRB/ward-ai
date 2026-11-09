package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.UnitScopes;
import dev.ward.analysis.domain.node.NodeScopeAnalysis;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Pairs a decipher's {@code update} with its {@code final} in TypeScript and JavaScript scopes. */
class NodeCallWithoutFollowUpCheckTest {

  private static final String GCM = "^['\"].*(-gcm|-ccm|-ocb|chacha20-poly1305)['\"]$";

  private static final NodeCallWithoutFollowUpCheck CHECK =
      new NodeCallWithoutFollowUpCheck(
          "createDecipheriv", 0, GCM, "update", "final", "update without final");

  @Test
  void anUpdateWithoutAFinalIsReportedAtTheBinding() {
    final String source =
        "const crypto = require('crypto');\n"
            + "function decrypt(key, iv, data) {\n"
            + "  const d = crypto.createDecipheriv('aes-256-gcm', key, iv);\n"
            + "  return d.update(data);\n"
            + "}\n";

    final List<RuleMatch> found = run(source, CHECK);

    assertThat(found).hasSize(1);
    assertThat(found.get(0).span().startLine()).isEqualTo(3);
    assertThat(found.get(0).span().startColumn()).isEqualTo(9);
    assertThat(found.get(0).message()).isEqualTo("update without final");
  }

  @Test
  void aFinalAfterTheUpdateIsClean() {
    final String source =
        "const crypto = require('crypto');\n"
            + "function decrypt(key, iv, data) {\n"
            + "  const d = crypto.createDecipheriv('aes-256-gcm', key, iv);\n"
            + "  const out = d.update(data);\n"
            + "  d.final();\n"
            + "  return out;\n"
            + "}\n";

    assertThat(run(source, CHECK)).isEmpty();
  }

  @Test
  void aNonAeadAlgorithmIsIgnored() {
    final String source =
        "const crypto = require('crypto');\n"
            + "function decrypt(key, iv, data) {\n"
            + "  const d = crypto.createDecipheriv('aes-256-cbc', key, iv);\n"
            + "  return d.update(data);\n"
            + "}\n";

    assertThat(run(source, CHECK)).isEmpty();
  }

  @Test
  void aBindingWithoutAnUpdateIsIgnored() {
    final String source =
        "const crypto = require('crypto');\n"
            + "function decrypt(key, iv) {\n"
            + "  const d = createDecipheriv('aes-256-gcm', key, iv);\n"
            + "  return d;\n"
            + "}\n";

    assertThat(run(source, CHECK)).isEmpty();
  }

  @Test
  void theArgumentIndexSelectsTheArgumentThatMustMatch() {
    final NodeCallWithoutFollowUpCheck second =
        new NodeCallWithoutFollowUpCheck(
            "createDecipheriv", 1, "^[\\w$]+$", "update", "final", "update without final");
    final String source =
        "function decrypt(data) {\n"
            + "  const d = createDecipheriv('aes-256-gcm', key, iv);\n"
            + "  return d.update(data);\n"
            + "}\n";

    assertThat(run(source, second)).hasSize(1);
  }

  private static List<RuleMatch> run(final String source, final NodeCallWithoutFollowUpCheck check) {
    final SourceUnit unit = new SourceUnit("m", AnalysisLanguage.JAVASCRIPT, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    final NodeScopeAnalysis analysis = new NodeScopeAnalysis(environments -> Set.of());
    final UnitScopes scopes = analysis.analyze(unit, root);
    return check.matches(root, null, new ScopeView(scopes, Map.of(), Set.of(), analysis), unit);
  }
}

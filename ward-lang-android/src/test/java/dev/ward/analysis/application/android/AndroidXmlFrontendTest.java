package dev.ward.analysis.application.android;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.android.AndroidSyntax;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AndroidXmlFrontendTest {

  @Test
  void aWellFormedFileIsATreeWithoutDiagnostics() {
    final ParseResult result =
        new AndroidXmlFrontend()
            .parse(new SourceUnit("AndroidManifest.xml", AnalysisLanguage.ANDROID, "<manifest/>"));

    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.tree().kind()).isEqualTo(AndroidSyntax.ELEMENT);
  }

  @Test
  void aSyntaxProblemIsADiagnosticOfTheFileNextToAnEmptyDocument() {
    final ParseResult result =
        new AndroidXmlFrontend()
            .parse(new SourceUnit("res/layout/a.xml", AnalysisLanguage.ANDROID, "<a>\n<b></a>"));

    assertThat(result.tree().kind()).isEqualTo(AndroidSyntax.DOCUMENT);
    assertThat(result.diagnostics())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.file()).isEqualTo("res/layout/a.xml");
              assertThat(finding.line()).isEqualTo(2);
              assertThat(finding.ruleId()).isEqualTo("syntax");
            });
  }

  @Test
  void everyRuleFileBodyNamesACheckThatIsRegistered() {
    final Map<String, CheckFactory> checks = new AndroidCheckProvider().checks();

    assertThat(checks)
        .containsOnlyKeys(
            "android-exported-component",
            "android-application-flag",
            "android-cleartext-traffic",
            "android-allow-backup",
            "android-hardcoded-text",
            "android-content-description");
    assertThat(
            checks
                .get("android-application-flag")
                .create(TomlValues.parse("attribute = \"debuggable\"\nmessage = \"m\"")))
        .isNotNull();
    assertThat(new AndroidRuleFiles().ruleFiles())
        .containsExactly("rules/android/manifest.toml", "rules/android/resources.toml");
  }
}

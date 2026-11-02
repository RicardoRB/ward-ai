package dev.ward.analysis.frontend.antlr;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AntlrLanguageFrontendTest {

  private static final String BROKEN_JAVA_PATH = "Broken.java";

  @ParameterizedTest
  @CsvSource({
    "JAVA, java-valid.java, compilationunit",
    "KOTLIN, kotlin-valid.kt, kotlinFile",
    "TYPESCRIPT, typescript-valid.ts, program",
    "JAVASCRIPT, javascript-valid.js, program",
    "PYTHON, python-valid.py, file_input",
    "SWIFT, swift-valid.swift, top_level"
  })
  void eachLanguageParsesItsFixtureWithoutDiagnostics(
      final AnalysisLanguage language, final String fixture, final String rootKind)
      throws IOException {
    final ParseResult result =
        new AntlrLanguageFrontend().parse(new SourceUnit(fixture, language, fixture(fixture)));

    assertThat(result.tree().kind()).isEqualTo(rootKind);
    assertThat(result.diagnostics()).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "JAVA, java-broken.java",
    "KOTLIN, kotlin-broken.kt",
    "TYPESCRIPT, typescript-broken.ts",
    "JAVASCRIPT, javascript-broken.js",
    "PYTHON, python-broken.py",
    "SWIFT, swift-broken.swift"
  })
  void eachLanguageReturnsPartialTreeAndDiagnosticsForBrokenFixture(
      final AnalysisLanguage language, final String fixture) throws IOException {
    final ParseResult result =
        new AntlrLanguageFrontend().parse(new SourceUnit(fixture, language, fixture(fixture)));

    assertThat(result.tree().children()).isNotEmpty();
    assertThat(result.diagnostics()).isNotEmpty();
    assertThat(result.diagnostics())
        .allSatisfy(finding -> assertThat(finding.file()).isEqualTo(fixture));
  }

  @Test
  void brokenJavaDiagnosticPointsAtTheErrorLine() throws IOException {
    final ParseResult result =
        new AntlrLanguageFrontend()
            .parse(
                new SourceUnit(
                    BROKEN_JAVA_PATH, AnalysisLanguage.JAVA, fixture("java-broken.java")));

    assertThat(result.diagnostics()).isNotEmpty();
    assertThat(result.diagnostics().getFirst().line()).isEqualTo(2);
    assertThat(result.diagnostics().getFirst().column()).isPositive();
  }

  @Test
  void missingSemicolonIsNamedInTheDiagnostic() {
    final ParseResult result =
        new AntlrLanguageFrontend()
            .parse(new SourceUnit("A.java", AnalysisLanguage.JAVA, "class A { int x = 3 }"));

    assertThat(result.diagnostics())
        .anySatisfy(finding -> assertThat(finding.message()).contains("';'"));
  }

  @Test
  void emptyFileReturnsWithoutThrowing() {
    final ParseResult result =
        new AntlrLanguageFrontend().parse(new SourceUnit("E.java", AnalysisLanguage.JAVA, ""));

    assertThat(result.tree().kind()).isEqualTo("compilationunit");
  }

  @Test
  void binaryTextReturnsWithoutThrowing() {
    final ParseResult result =
        new AntlrLanguageFrontend()
            .parse(new SourceUnit("B.java", AnalysisLanguage.JAVA, "\u0000\u0001ÿ class \u0007 ￿"));

    assertThat(result.tree()).isNotNull();
    assertThat(result.diagnostics()).isNotEmpty();
  }

  @Test
  void unterminatedStringReturnsWithoutThrowing() {
    final ParseResult result =
        new AntlrLanguageFrontend()
            .parse(new SourceUnit("S.java", AnalysisLanguage.JAVA, "class S { String s = \"abc"));

    assertThat(result.diagnostics()).isNotEmpty();
  }

  @Test
  void diagnosticsDoNotCarryOverBetweenUnits() throws IOException {
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    frontend.parse(
        new SourceUnit(BROKEN_JAVA_PATH, AnalysisLanguage.JAVA, fixture("java-broken.java")));

    final ParseResult valid =
        frontend.parse(
            new SourceUnit("ok.java", AnalysisLanguage.JAVA, fixture("java-valid.java")));

    assertThat(valid.diagnostics()).isEmpty();
  }

  private static String fixture(final String name) throws IOException {
    try (InputStream in =
        AntlrLanguageFrontendTest.class.getResourceAsStream("/fixtures/" + name)) {
      if (in == null) {
        throw new IOException("missing fixture " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}

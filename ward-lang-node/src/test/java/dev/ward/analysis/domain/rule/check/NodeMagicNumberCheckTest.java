package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the magic-number check on parsed TypeScript. */
class NodeMagicNumberCheckTest {

  private static final int HEX_VALUE = 31;
  private static final int BINARY_VALUE = 5;
  private static final int THOUSAND = 1000;
  private static final int OCTAL_VALUE = 15;
  private static final NodeMagicNumberFlags DEFAULTS =
      new NodeMagicNumberFlags(false, false, false, false, false);
  private static final NodeMagicNumberFlags ENFORCE_CONST =
      new NodeMagicNumberFlags(false, false, false, false, true);
  private static final NodeMagicNumberFlags OBJECTS =
      new NodeMagicNumberFlags(true, false, false, false, false);

  @Test
  void declarationInitializerIsSilentByDefault() {
    assertThat(run(DEFAULTS, "export const PORT = 3000;\nlet sum = 3;\n")).isEmpty();
  }

  @Test
  void letOrVarInitializerIsReportedWithEnforceConstAndConstIsNot() {
    final List<RuleMatch> found =
        run(ENFORCE_CONST, "let port = 3000;\nvar sum = 3;\nconst x = 4;\n");

    assertThat(found).hasSize(2);
    assertThat(found.get(0).message()).isEqualTo("use const");
  }

  @Test
  void propertyValueIsSilentByDefault() {
    assertThat(run(DEFAULTS, "export const c = { lines: 70 };\n")).isEmpty();
  }

  @Test
  void propertyValueIsReportedWithDetectObjects() {
    final List<RuleMatch> found = run(OBJECTS, "export const c = { lines: 70 };\n");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).message()).isEqualTo("70 is magic");
  }

  @Test
  void assignmentToMemberIsSilentByDefaultAndIdentifierIsReported() {
    assertThat(run(DEFAULTS, "o.p = 10;\n")).isEmpty();
    assertThat(run(OBJECTS, "o.p = 10;\n")).hasSize(1);
    assertThat(run(DEFAULTS, "x = 9;\n")).hasSize(1);
  }

  @Test
  void negatedLiteralIsReportedAtTheSign() {
    final List<RuleMatch> found = run(DEFAULTS, "f(-12);\n");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).span().startOffset()).isEqualTo(2);
    assertThat(found.get(0).message()).isEqualTo("-12 is magic");
  }

  @Test
  void numbersWithAnyBaseAndSeparatorsAreParsed() {
    assertThat(NodeMagicNumberCheck.value("HexIntegerLiteral", "0x1F"))
        .contains(BigDecimal.valueOf(HEX_VALUE));
    assertThat(NodeMagicNumberCheck.value("BinaryIntegerLiteral", "0b101"))
        .contains(BigDecimal.valueOf(BINARY_VALUE));
    assertThat(NodeMagicNumberCheck.value("DecimalLiteral", "1_000"))
        .contains(BigDecimal.valueOf(THOUSAND));
    assertThat(NodeMagicNumberCheck.value("OctalIntegerLiteral2", "017"))
        .contains(BigDecimal.valueOf(OCTAL_VALUE));
  }

  @Test
  void ignoredValueIsSilent() {
    final NodeMagicNumberCheck check =
        new NodeMagicNumberCheck(
            List.of(BigDecimal.valueOf(9)), DEFAULTS, "{value} is magic", "use const");

    assertThat(check.matches(parse("f(9);\n"))).isEmpty();
  }

  private static List<RuleMatch> run(final NodeMagicNumberFlags flags, final String source) {
    return new NodeMagicNumberCheck(List.of(), flags, "{value} is magic", "use const")
        .matches(parse(source));
  }

  private static SyntaxNode parse(final String source) {
    final SourceUnit unit = new SourceUnit("m", AnalysisLanguage.TYPESCRIPT, source);
    return new AntlrLanguageFrontend().parse(unit).tree();
  }
}

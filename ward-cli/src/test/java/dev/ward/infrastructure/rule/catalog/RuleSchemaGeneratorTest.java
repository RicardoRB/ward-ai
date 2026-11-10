package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleOption;
import dev.ward.analysis.domain.rule.RuleOptionType;
import dev.ward.analysis.domain.rule.RulePreset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleSchemaGeneratorTest {

  private static final RuleDefinition SIZES =
      new RuleDefinition(
          "ward:java/method-length",
          Set.of(AnalysisLanguage.JAVA),
          RuleCapability.SYNTAX,
          AnalysisSeverity.WARNING,
          "",
          "sizes",
          Set.of(RulePreset.STANDARD),
          List.of(
              new RuleOption("max", RuleOptionType.INTEGER, null, 1, 500, List.of()),
              new RuleOption(
                  "mode",
                  RuleOptionType.ENUM,
                  null,
                  Long.MIN_VALUE,
                  Long.MAX_VALUE,
                  List.of("a", "b")),
              new RuleOption("flag", RuleOptionType.BOOLEAN),
              new RuleOption("names", RuleOptionType.STRING_LIST),
              new RuleOption("pattern", RuleOptionType.REGEX),
              new RuleOption("label", RuleOptionType.STRING)),
          0);

  private static final RuleDefinition NAMING =
      new RuleDefinition(
          "ward:typescript/function-name",
          Set.of(AnalysisLanguage.TYPESCRIPT),
          RuleCapability.SYMBOLS,
          AnalysisSeverity.ERROR,
          "",
          "naming",
          Set.of(),
          List.of(),
          0);

  @Test
  void rulesListTheirOptionsWithTheirDeclaredTypesAndBounds() {
    final String schema = RuleSchema.render(List.of(SIZES));

    assertThat(schema).contains("\"ward:java/method-length\": {");
    assertThat(schema)
        .contains("\"max\": {\n")
        .contains("\"minimum\": 1")
        .contains("\"maximum\": 500");
    assertThat(schema).contains("\"flag\": {\n").contains("\"type\": \"boolean\"");
    assertThat(schema).contains("\"names\": {\n").contains("\"type\": \"array\"");
    assertThat(schema)
        .contains("\"pattern\": {\n")
        .contains("\"description\": \"A regular expression.\"");
  }

  @Test
  void enumOptionsListTheirValues() {
    final String schema = RuleSchema.render(List.of(SIZES));

    assertThat(schema).contains("\"mode\": {").contains("\"enum\": [\n").contains("\"a\",\n");
    assertThat(schema).contains("\"b\"\n");
  }

  @Test
  void eachRuleAllowsSeverityAndExcludePathsOnly() {
    final String schema = RuleSchema.render(List.of(SIZES));

    assertThat(schema).contains("\"severity\": {");
    assertThat(schema).contains("\"error\",\n").contains("\"warning\"\n");
    assertThat(schema).contains("\"exclude-paths\": {");
    assertThat(schema).contains("\"additionalProperties\": false");
  }

  @Test
  void selectListsPresetsLanguagesCategoriesAndRuleIds() {
    final String schema = RuleSchema.render(List.of(SIZES, NAMING));

    assertThat(schema).contains("\"preset\": {").contains("\"none\"").contains("\"strict\"");
    assertThat(schema).contains("\"java\"").contains("\"typescript\"");
    assertThat(schema).contains("\"naming\"").contains("\"sizes\"");
    assertThat(schema).contains("\"include\": {").contains("\"ward:typescript/function-name\"");
  }

  @Test
  void versionIsTheSupportedConstant() {
    assertThat(RuleSchema.render(List.of(SIZES)))
        .contains("\"version\": {\n")
        .contains("\"const\": 1");
  }

  @Test
  void theOutputDoesNotDependOnTheOrderOfTheDefinitions() {
    assertThat(RuleSchema.render(List.of(NAMING, SIZES)))
        .isEqualTo(RuleSchema.render(List.of(SIZES, NAMING)));
  }

  @Test
  void generatingTwiceGivesTheSameText() {
    assertThat(RuleSchema.render(List.of(SIZES))).isEqualTo(RuleSchema.render(List.of(SIZES)));
  }

  @Test
  void escapesQuotesAndBackslashesInEnumValues() {
    final RuleDefinition quoted =
        new RuleDefinition(
            "ward:java/quoted",
            Set.of(AnalysisLanguage.JAVA),
            RuleCapability.SYNTAX,
            AnalysisSeverity.WARNING,
            "",
            "",
            Set.of(),
            List.of(
                new RuleOption(
                    "mode",
                    RuleOptionType.ENUM,
                    null,
                    Long.MIN_VALUE,
                    Long.MAX_VALUE,
                    List.of("say \"hi\" \\ twice"))),
            0);

    assertThat(RuleSchema.render(List.of(quoted))).contains("\"say \\\"hi\\\" \\\\ twice\"");
  }

  @Test
  void endsWithALineBreakAfterTheRootObject() {
    assertThat(RuleSchema.render(List.of(SIZES))).startsWith("{\n").endsWith("}\n");
  }
}

package dev.ward.analysis.frontend.antlr;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.format.ecma.TypeScriptFormatter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * JSX in {@code .tsx} and {@code .jsx} units, and the TypeScript cast that must keep its meaning.
 */
class JsxSyntaxTest {

  private static final Path SYNTAX = Path.of("src/test/resources/syntax");

  private static final List<String> JSX_KINDS =
      List.of(
          "jsxElement",
          "jsxSelfClosingElement",
          "jsxOpeningElement",
          "jsxClosingElement",
          "jsxFragment",
          "jsxAttribute",
          "jsxSpreadAttribute",
          "jsxAttributeValue",
          "jsxExpressionContainer",
          "jsxText",
          "jsxElementName");

  @Test
  void tsxComponentParsesWithEveryJsxNodeKind() throws IOException {
    final ParseResult result = parse(AnalysisLanguage.TYPESCRIPT, "component.tsx");

    assertThat(result.diagnostics()).isEmpty();
    assertThat(kindsOf(result.tree())).containsAll(JSX_KINDS);
  }

  @Test
  void jsxComponentParsesWithEveryJsxNodeKind() throws IOException {
    final ParseResult result = parse(AnalysisLanguage.JAVASCRIPT, "component.jsx");

    assertThat(result.diagnostics()).isEmpty();
    assertThat(kindsOf(result.tree())).containsAll(JSX_KINDS);
  }

  @Test
  void typeScriptCastStaysATypeAssertion() throws IOException {
    final ParseResult result = parse(AnalysisLanguage.TYPESCRIPT, "type-assertion-cast.ts");

    assertThat(result.diagnostics()).isEmpty();
    assertThat(kindsOf(result.tree())).contains("typeArguments").doesNotContain("jsxElement");
  }

  @Test
  void typeScriptFileDoesNotParseJsxElements() {
    final ParseResult result =
        parseText(AnalysisLanguage.TYPESCRIPT, "view.ts", "const view = <div title=\"a\" />;\n");

    assertThat(result.diagnostics()).isNotEmpty();
    assertThat(kindsOf(result.tree())).doesNotContain("jsxElement");
  }

  @Test
  void tsxKeepsLessThanComparisons() {
    final ParseResult result =
        parseText(AnalysisLanguage.TYPESCRIPT, "compare.tsx", "const less = a < b && c > d;\n");

    assertThat(result.diagnostics()).isEmpty();
    assertThat(kindsOf(result.tree())).doesNotContain("jsxElement");
  }

  @Test
  void tsxFragmentAndNamespacedNamesParse() {
    final ParseResult result =
        parseText(
            AnalysisLanguage.TYPESCRIPT,
            "frag.tsx",
            "const v = <><svg:rect x=\"1\" /><ui.Header/></>;\n");

    assertThat(result.diagnostics()).isEmpty();
    assertThat(kindsOf(result.tree())).contains("jsxFragment", "jsxElementName");
  }

  @Test
  void formatterKeepsJsxTextAndLayoutOfTsx() {
    final String text = "const view = (\n  <div   title = \"a\"   >  hi   there  </div>\n);\n";
    final TypeScriptFormatter formatter = new TypeScriptFormatter();

    final FormatResult result = formatter.format(text, FormatOptions.defaults(formatter.options()));

    final String printed =
        result instanceof FormatResult.Formatted formatted ? formatted.text() : text;
    assertThat(result).isNotInstanceOf(FormatResult.Refused.class);
    assertThat(printed).contains("<div   title = \"a\"   >  hi   there  </div>");
  }

  private static ParseResult parse(final AnalysisLanguage language, final String fixture)
      throws IOException {
    final String directory =
        fixture.endsWith(".tsx") ? "tsx" : fixture.endsWith(".jsx") ? "jsx" : "typescript";
    final String text =
        Files.readString(SYNTAX.resolve(directory).resolve(fixture), StandardCharsets.UTF_8);
    return parseText(language, fixture, text);
  }

  private static ParseResult parseText(
      final AnalysisLanguage language, final String path, final String text) {
    return new AntlrLanguageFrontend().parse(new SourceUnit(path, language, text));
  }

  private static List<String> kindsOf(final SyntaxNode node) {
    return Stream.concat(
            Stream.of(node.kind()),
            node.children().stream().flatMap(child -> kindsOf(child).stream()))
        .toList();
  }
}

package dev.ward.analysis.frontend.antlr;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import dev.ward.analysis.domain.format.FormatSafety;
import dev.ward.analysis.domain.format.SourceFormatter;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.format.ecma.JavaScriptFormatSyntax;
import dev.ward.analysis.frontend.antlr.format.ecma.JavaScriptFormatter;
import dev.ward.analysis.frontend.antlr.format.ecma.TypeScriptFormatSyntax;
import dev.ward.analysis.frontend.antlr.format.ecma.TypeScriptFormatter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Every syntax fixture parses without errors and formats to a stable result. */
class EcmaSyntaxFixturesTest {

  private static final Path TYPESCRIPT = Path.of("src/test/resources/syntax/typescript");
  private static final Path JAVASCRIPT = Path.of("src/test/resources/syntax/javascript");

  /**
   * The node kinds each TypeScript fixture must produce. A construct the grammar only accepts by
   * accident, such as a misread {@code satisfies} as a variable name, leaves its kind out.
   */
  private static final Map<String, List<String>> TYPESCRIPT_KINDS =
      Map.ofEntries(
          Map.entry("accessor-field.ts", List.of("accessorModifier")),
          Map.entry("type-assertion-cast.ts", List.of("typeArguments")),
          Map.entry("ambient-string-module.ts", List.of("namespaceDeclaration")),
          Map.entry("const-type-params.ts", List.of("constModifier")),
          Map.entry("declaration-file.ts", List.of("namespaceDeclaration", "tupleElementType")),
          Map.entry("decorators.ts", List.of("decorator")),
          Map.entry("enum.ts", List.of("enumDeclaration")),
          Map.entry("export-named.ts", List.of("exportAliasName")),
          Map.entry("export-type.ts", List.of("typeOnlyModifier")),
          Map.entry("import-attributes.ts", List.of("importAttributes")),
          Map.entry("import-meta.ts", List.of("importMetaExpression")),
          Map.entry("import-type.ts", List.of("typeOnlyModifier")),
          Map.entry("infer-extends.ts", List.of("inferType")),
          Map.entry("optional-chain-non-null.ts", List.of("'?.'", "'!'")),
          Map.entry("optional-call.ts", List.of("arguments")),
          Map.entry("private-names.ts", List.of("privateIdentifier")),
          Map.entry("readonly-operator.ts", List.of("readonlyType")),
          Map.entry("regex-v.ts", List.of("RegularExpressionLiteral")),
          Map.entry("satisfies.ts", List.of("satisfiesClause")),
          Map.entry("template-literal-types.ts", List.of("templateStringType")),
          Map.entry("type-alias.ts", List.of("typeAliasDeclaration")),
          Map.entry("type-only-specifiers.ts", List.of("typeOnlyModifier")),
          Map.entry("using.ts", List.of("usingDeclaration")),
          Map.entry("variance.ts", List.of("varianceModifier")));

  /** The node kinds each JavaScript fixture must produce; see {@link #TYPESCRIPT_KINDS}. */
  private static final Map<String, List<String>> JAVASCRIPT_KINDS =
      Map.ofEntries(
          Map.entry("accessor-field.js", List.of("accessorModifier")),
          Map.entry("decorators.js", List.of("decorator")),
          Map.entry("export-named.js", List.of("exportAliasName")),
          Map.entry("import-attributes.js", List.of("importAttributes")),
          Map.entry("import-meta.js", List.of("importMetaExpression")),
          Map.entry("regex-v.js", List.of("RegularExpressionLiteral")),
          Map.entry("using.js", List.of("usingDeclaration")));

  static List<Path> typeScriptFixtures() throws IOException {
    return fixtures(TYPESCRIPT);
  }

  static List<Path> javaScriptFixtures() throws IOException {
    return fixtures(JAVASCRIPT);
  }

  private static List<Path> fixtures(final Path root) throws IOException {
    try (Stream<Path> files = Files.list(root)) {
      return files.sorted().toList();
    }
  }

  @ParameterizedTest(name = "typescript {0}")
  @MethodSource("typeScriptFixtures")
  void typeScriptFixtureParsesAndFormatsStably(final Path fixture) throws IOException {
    assertParsesAndFormatsStably(
        AnalysisLanguage.TYPESCRIPT,
        new TypeScriptFormatter(),
        new FormatSafety(new TypeScriptFormatSyntax()),
        fixture,
        TYPESCRIPT_KINDS);
  }

  @ParameterizedTest(name = "javascript {0}")
  @MethodSource("javaScriptFixtures")
  void javaScriptFixtureParsesAndFormatsStably(final Path fixture) throws IOException {
    assertParsesAndFormatsStably(
        AnalysisLanguage.JAVASCRIPT,
        new JavaScriptFormatter(),
        new FormatSafety(new JavaScriptFormatSyntax()),
        fixture,
        JAVASCRIPT_KINDS);
  }

  @Test
  void everyFixtureDeclaresItsNodeKinds() throws IOException {
    assertThat(typeScriptFixtures().stream().map(path -> path.getFileName().toString()))
        .containsExactlyInAnyOrderElementsOf(TYPESCRIPT_KINDS.keySet());
    assertThat(javaScriptFixtures().stream().map(path -> path.getFileName().toString()))
        .containsExactlyInAnyOrderElementsOf(JAVASCRIPT_KINDS.keySet());
  }

  private static void assertParsesAndFormatsStably(
      final AnalysisLanguage language,
      final SourceFormatter formatter,
      final FormatSafety safety,
      final Path fixture,
      final Map<String, List<String>> kinds)
      throws IOException {
    final String text = Files.readString(fixture, StandardCharsets.UTF_8);
    final ParseResult parsed =
        new AntlrLanguageFrontend().parse(new SourceUnit("fixture", language, text));
    assertThat(parsed.diagnostics()).as("parse diagnostics").isEmpty();
    final List<String> kindsOfTree = new ArrayList<>();
    collectKinds(parsed.tree(), kindsOfTree);
    assertThat(kindsOfTree)
        .as("node kinds of %s", fixture.getFileName())
        .containsAll(kinds.get(fixture.getFileName().toString()));

    final FormatOptions options = FormatOptions.defaults(formatter.options());
    final FormatResult once = safety.formatSafely(formatter, options, text);
    assertThat(once).as("format result").isNotInstanceOf(FormatResult.Refused.class);
    final String formatted = textOf(once, text);
    assertThat(textOf(safety.formatSafely(formatter, options, formatted), formatted))
        .as("second format")
        .isEqualTo(formatted);
  }

  private static void collectKinds(final SyntaxNode node, final List<String> kinds) {
    kinds.add(node.kind());
    node.children().forEach(child -> collectKinds(child, kinds));
  }

  private static String textOf(final FormatResult result, final String fallback) {
    return result instanceof FormatResult.Formatted formatted ? formatted.text() : fallback;
  }
}

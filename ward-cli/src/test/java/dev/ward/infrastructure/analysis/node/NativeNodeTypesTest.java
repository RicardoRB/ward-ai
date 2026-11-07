package dev.ward.infrastructure.analysis.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import dev.ward.infrastructure.analysis.TypeFactsFixture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks the native TypeScript and JavaScript type facts against the fixtures tsc answered. */
class NativeNodeTypesTest {
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/native-types");
  private static final String PATH = "src/app.ts";
  private static final String LIB = "node_modules/typescript/lib/lib.es5.d.ts";
  private static final String MATH =
      "interface Math { max(...values: number[]): number; }\ndeclare var Math: Math;\n";
  private static final String USE = "src/use.ts";
  private static final String PACKAGE_INDEX = "node_modules/pkg/lib/index.d.ts";

  @TempDir Path project;

  /** Every TypeScript {@code types.json} fixture must be compared below; a new one fails this test. */
  @Test
  void everyTypeScriptTypesJsonFixtureIsCovered() throws IOException {
    try (Stream<Path> files = Files.walk(Path.of("src/test/resources"))) {
      final List<Path> fixtures =
          files
              .filter(file -> "types.json".equals(file.getFileName().toString()))
              .filter(file -> Files.isRegularFile(file.resolveSibling("tsconfig.json")))
              .toList();
      assertThat(fixtures).containsExactly(FIXTURES.resolve("typescript/types.json"));
    }
  }

  @Test
  void answersTheSameFactsAsTheFixtureJson() throws IOException {
    write(PATH, Files.readString(FIXTURES.resolve("typescript/src/app.ts")));
    write(LIB, MATH);
    write("node_modules/typescript/lib/lib.d.ts", "/// <reference lib=\"es5\" />\n");
    final String text = Files.readString(project.resolve(PATH));
    final UnitTypeFacts expected = TypeFactsFixture.read(FIXTURES.resolve("typescript/types.json"));
    final Span max = span(text, "Math.max(1, 2)");
    final Span base =
        span(text, text.substring(text.indexOf("abstract class Base"), text.indexOf("class Impl") - 1));
    final Span impl =
        span(text, text.substring(text.indexOf("class Impl"), text.indexOf("const biggest") - 1));
    final Span run = span(text, "run(): number {\n    return 1;\n  }");

    final UnitTypeFacts actual =
        new NativeNodeTypes(project)
            .facts()
            .factsOf(
                new SourceUnit(PATH, AnalysisLanguage.TYPESCRIPT, text),
                List.of(max, base, impl, run),
                EnumSet.allOf(FactKind.class));

    assertThat(actual.callTarget(max)).isEqualTo(expected.callTarget(max)).isPresent();
    assertThat(actual.typeOf(max)).isEqualTo(expected.typeOf(max)).isPresent();
    assertThat(actual.shapeOf(base)).isEqualTo(expected.shapeOf(base)).isPresent();
    assertThat(actual.shapeOf(impl)).isEqualTo(expected.shapeOf(impl)).isPresent();
    assertThat(actual.at(run).overrides()).isEqualTo(expected.at(run).overrides()).hasSize(1);
    assertThat(actual.typeOf(span(text, "nope.value"))).isEmpty();
  }

  @Test
  void resolvesPackageDeclarationsAndKeepsTheirIndexByPackageHash() throws IOException {
    write("node_modules/pkg/package.json", "{\"name\":\"pkg\",\"types\":\"lib/index.d.ts\"}");
    write(PACKAGE_INDEX, "export declare function load(id: number): Promise<string>;\n");
    final String text = "import { load } from \"pkg\";\nconst result = load(1);\n";
    write(USE, text);
    final Span call = span(text, "load(1)");
    final SourceUnit unit = new SourceUnit(USE, AnalysisLanguage.TYPESCRIPT, text);

    final UnitTypeFacts cold = ask(unit, call);
    final long cached = cacheFiles();
    final UnitTypeFacts warm = ask(unit, call);

    assertThat(cold.typeOf(call)).map(TypeRef::name).contains("Promise<string>");
    assertThat(warm.typeOf(call)).isEqualTo(cold.typeOf(call));
    assertThat(cached).isPositive();
    assertThat(cacheFiles()).isEqualTo(cached);

    write(PACKAGE_INDEX, "export declare function load(id: number): Promise<number>;\n");
    assertThat(ask(unit, call).typeOf(call)).map(TypeRef::name).contains("Promise<number>");
    assertThat(cacheFiles()).isGreaterThan(cached);
  }

  @Test
  void leavesTypesUnknownWithoutThePackagesOrTheTypeScriptLibrary() throws IOException {
    final String text =
        "import { load } from \"missing\";\nconst a = load(1);\nconst b = Math.max(1, 2);\n";
    write(USE, text);
    final SourceUnit unit = new SourceUnit(USE, AnalysisLanguage.TYPESCRIPT, text);

    final UnitTypeFacts facts = ask(unit, span(text, "load(1)"), span(text, "Math.max(1, 2)"));

    assertThat(facts.typeOf(span(text, "load(1)"))).isEmpty();
    assertThat(facts.typeOf(span(text, "Math.max(1, 2)"))).isEmpty();
  }

  private UnitTypeFacts ask(final SourceUnit unit, final Span... spans) {
    return new NativeNodeTypes(project)
        .facts()
        .factsOf(unit, List.of(spans), EnumSet.allOf(FactKind.class));
  }

  private long cacheFiles() throws IOException {
    final Path cache = project.resolve(".ward/cache/types/node");
    if (!Files.isDirectory(cache)) {
      return 0;
    }
    try (Stream<Path> files = Files.list(cache)) {
      return files.count();
    }
  }

  private void write(final String path, final String text) throws IOException {
    final Path file = project.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }

  private static Span span(final String text, final String token) {
    final int offset = text.lastIndexOf(token);
    final int line = (int) text.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
    final int column = offset - text.lastIndexOf('\n', offset - 1);
    final int last = offset + token.length() - 1;
    final int endLine = (int) text.substring(0, last).chars().filter(c -> c == '\n').count() + 1;
    final int endColumn = last - text.lastIndexOf('\n', last - 1);
    return new Span(line, column, endLine, endColumn, offset, last + 1);
  }
}

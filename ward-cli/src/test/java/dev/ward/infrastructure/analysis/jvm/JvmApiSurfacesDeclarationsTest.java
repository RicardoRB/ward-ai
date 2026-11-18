package dev.ward.infrastructure.analysis.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JvmApiSurfacesDeclarationsTest {
  private static final Path ROOT = Path.of("src/test/resources/api-compat");
  private static final int MINIMUM_SIDES = 40;
  private static final Path NOWHERE = Path.of(".");
  private static final String JAVA_SAMPLE =
      """
      package p;
      import java.util.function.Supplier;
      public class Shapes<T> {
        public static final int[] SIZES = {1, 2, 3};
        public final Supplier<String> name = () -> { return "x"; };
        public final Object anonymous = new Object() { public String toString() { return ""; } };
        static { System.out.println("loaded"); }
        { System.out.println("created"); }
        public Shapes() { this(1); }
        public Shapes(int n) { if (n > 0) { n--; } }
        public <R> R map(java.util.function.Function<T, R> f) throws Exception { return null; }
        public interface Visitor { default void visit() { int i = 0; } void done(); }
        public enum Kind { A { void f() { } }, B; public int rank() { return 1; } }
        public record Point(int x, int y) { public Point { if (x < 0) { throw new Error(); } } }
        public @interface Tag { String[] value() default {"a", "b"}; }
        public class Inner { public int size() { return 0; } }
      }
      """;
  private static final String KOTLIN_SAMPLE =
      """
      package p
      class Shapes(val size: Int = 1) {
        val area: Int
          get() { return size * size }
        var label: String = "a"
          set(value) { field = value.trim() }
        val inferred = run { listOf(1, 2) }
        val lazyName by lazy { "x" }
        val greeting = "size ${size + 1} ${"nested ${size}"}"
        init { println("created") }
        constructor(text: String) : this(text.length) { println(text) }
        fun double(): Int { return size * 2 }
        fun triple() = size * 3
        fun mapped() = listOf(1).map { it + 1 }
        fun <T> pick(a: T, b: T): T where T : Comparable<T> { return if (a > b) a else b }
        enum class Kind { A { override fun toString() = "a" }, B; fun rank(): Int { return 1 } }
        companion object { fun make(): Shapes { return Shapes() } }
        interface Visitor { fun visit() { println() } }
        object Registry { fun all(): List<Shapes> { return emptyList() } }
        val anon = object { fun f() { } }
      }
      fun top(): Int { return 1 }
      val topInferred = listOf(1).map { it * 2 }
      """;

  @Test
  @DisplayName("the surface is read without method bodies")
  void surfaceEqualsTheFullParseOnEveryFixture() throws IOException {
    // Given
    final List<SourceUnit> samples =
        List.of(
            new SourceUnit("p/Shapes.java", AnalysisLanguage.JAVA, JAVA_SAMPLE),
            new SourceUnit("p/Shapes.kt", AnalysisLanguage.KOTLIN, KOTLIN_SAMPLE)
        );
    final List<List<SourceUnit>> sides = new ArrayList<>(List.of(samples.subList(0, 1)));
    sides.add(samples.subList(1, 2));
    try (Stream<Path> sideDirs = fixtureSides()) {
      sideDirs.forEach(side -> sides.add(units(side)));
    }
    final JvmApiSurfaces surfaces = new JvmApiSurfaces();

    // When / Then
    assertThat(sides).hasSizeGreaterThan(MINIMUM_SIDES);
    samples.forEach(
        sample ->
            assertThat(surfaces.declarations(sample).text())
                .hasSameSizeAs(sample.text())
                .isNotEqualTo(sample.text())
    );
    for (final List<SourceUnit> side : sides) {
      final List<SourceUnit> reduced = side.stream().map(surfaces::declarations).toList();
      assertThat(read(surfaces, reduced)).isEqualTo(read(surfaces, side));
    }
  }

  @Test
  @DisplayName("the bodies are not parsed")
  void bodiesAreNotParsed() {
    // Given
    final String javaBroken = JAVA_SAMPLE.replace("return 0;", "return 0 ((( ;; ]]");
    final String kotlinBroken = KOTLIN_SAMPLE.replace("return size * 2", "return ((( size ]]");
    final JvmApiSurfaces surfaces = new JvmApiSurfaces();
    final List<SourceUnit> broken =
        List.of(
            new SourceUnit("p/Shapes.java", AnalysisLanguage.JAVA, javaBroken),
            new SourceUnit("p/Shapes.kt", AnalysisLanguage.KOTLIN, kotlinBroken)
        );
    final List<SourceUnit> valid =
        List.of(
            new SourceUnit("p/Shapes.java", AnalysisLanguage.JAVA, JAVA_SAMPLE),
            new SourceUnit("p/Shapes.kt", AnalysisLanguage.KOTLIN, KOTLIN_SAMPLE)
        );

    // When
    final List<ApiType> brokenSurface =
        surfaces.read(NOWHERE, broken.stream().map(surfaces::declarations).toList());

    // Then
    assertThat(brokenSurface).isNotEmpty().isEqualTo(surfaces.read(NOWHERE, valid));
  }

  private static List<ApiType> read(final JvmApiSurfaces surfaces, final List<SourceUnit> units) {
    return surfaces.read(NOWHERE, units);
  }

  private static Stream<Path> fixtureSides() throws IOException {
    final List<Path> sides = new ArrayList<>();
    for (final String language : List.of("java", "kotlin")) {
      try (Stream<Path> fixtures = Files.list(ROOT.resolve(language))) {
        for (final Path fixture : fixtures.filter(Files::isDirectory).sorted().toList()) {
          sides.add(fixture.resolve("baseline"));
          sides.add(fixture.resolve("revision"));
        }
      }
    }
    return sides.stream();
  }

  private static List<SourceUnit> units(final Path side) {
    try (Stream<Path> files = Files.walk(side)) {
      return files
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".kt"))
          .sorted()
          .map(path -> unit(side, path))
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static SourceUnit unit(final Path side, final Path path) {
    try {
      return new SourceUnit(
          side.relativize(path).toString().replace('\\', '/'),
          path.toString().endsWith(".kt") ? AnalysisLanguage.KOTLIN : AnalysisLanguage.JAVA,
          Files.readString(path)
      );
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

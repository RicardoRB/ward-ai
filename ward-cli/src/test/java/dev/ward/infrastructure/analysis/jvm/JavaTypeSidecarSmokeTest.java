package dev.ward.infrastructure.analysis.jvm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.ExpressionTypes;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import dev.ward.infrastructure.analysis.TypeFactsFixture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the real Java type sidecar on the {@code type-sidecar/java} fixture. Skipped unless {@code
 * -Dward.sidecar.java=true}; needs a {@code java} with {@code jdk.compiler} on the PATH.
 */
class JavaTypeSidecarSmokeTest {
  private static final Path FIXTURE =
      Path.of("src/test/resources/fixtures/type-sidecar/java/src/main/java/app/User.java");
  private static final Path TYPES_JSON =
      Path.of("src/test/resources/fixtures/type-sidecar/java/types.json");
  private static final String PATH = "src/main/java/app/User.java";

  @TempDir Path project;

  @Test
  void typesCallSitesAndLeavesUnresolvableLibraryTypesOut() throws IOException {
    assumeTrue(Boolean.getBoolean("ward.sidecar.java"), "set -Dward.sidecar.java=true");
    final Path source = project.resolve(PATH);
    Files.createDirectories(source.getParent());
    Files.copy(FIXTURE, source);
    final String text = Files.readString(source);
    final Span getName = span(text, "getName()", true);
    final Span port = span(text, "port()", true);
    final Span missing = span(text, "Missing", false);

    final JavaTypeSidecar sidecar = new JavaTypeSidecar(project, "smoke");
    try {
      final Optional<TypeFacts> resolver = sidecar.start();
      assertThat(resolver).isPresent();
      final ExpressionTypes answer =
          resolver
              .orElseThrow()
              .typesOf(
                  new SourceUnit(PATH, AnalysisLanguage.JAVA, text),
                  List.of(getName, port, missing));
      assertThat(answer.types())
          .containsEntry(getName, "java.lang.String")
          .containsEntry(port, "int")
          .doesNotContainKey(missing);
    } finally {
      sidecar.close();
    }
  }

  @Test
  void answersTheSameFactsAsTheFixtureJson() throws IOException {
    assumeTrue(Boolean.getBoolean("ward.sidecar.java"), "set -Dward.sidecar.java=true");
    final Path source = project.resolve(PATH);
    Files.createDirectories(source.getParent());
    Files.copy(FIXTURE, source);
    final String text = Files.readString(source);
    final UnitTypeFacts expected = TypeFactsFixture.read(TYPES_JSON);
    final Span equalsCall = span(text, "value.equals(\"x\")", true);
    final Span toString = span(text, "@Override public String toString() { return name; }", true);
    final Span read = span(text, "public void read() throws java.io.IOException {}", true);
    final Span kind = span(text, "\"user\"", true);
    final Span user =
        span(
            text,
            text.substring(text.indexOf("public class User"), text.lastIndexOf('}') + 1),
            true);

    final JavaTypeSidecar sidecar = new JavaTypeSidecar(project, "smoke");
    try {
      final TypeFacts facts = sidecar.start(classpathFile()).orElseThrow();
      final UnitTypeFacts actual =
          facts.factsOf(
              new SourceUnit(PATH, AnalysisLanguage.JAVA, text),
              List.of(equalsCall, toString, read, kind, user),
              EnumSet.allOf(FactKind.class));
      assertThat(actual.callTarget(equalsCall))
          .isEqualTo(expected.callTarget(equalsCall))
          .isPresent();
      assertThat(actual.typeOf(equalsCall)).isEqualTo(expected.typeOf(equalsCall));
      assertThat(actual.at(toString).annotations()).isEqualTo(expected.at(toString).annotations());
      assertThat(actual.at(toString).overrides()).isEqualTo(expected.at(toString).overrides());
      assertThat(actual.at(read).thrown()).isEqualTo(expected.at(read).thrown());
      assertThat(actual.constant(kind)).isEqualTo(expected.constant(kind)).isPresent();
      assertThat(actual.shapeOf(user)).isEqualTo(expected.shapeOf(user)).isPresent();
    } finally {
      sidecar.close();
    }
  }

  /** A classpath file naming one empty directory: enough to start the sidecar. */
  private Optional<Path> classpathFile() throws IOException {
    final Path libs = Files.createDirectories(project.resolve("libs"));
    return Optional.of(Files.writeString(project.resolve("classpath.txt"), libs.toString()));
  }

  /** The span of a token in {@code text}, its last occurrence when {@code last} is set. */
  static Span span(final String text, final String token, final boolean last) {
    final int offset = last ? text.lastIndexOf(token) : text.indexOf(token);
    final int line = (int) text.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
    final int column = offset - text.lastIndexOf('\n', offset - 1);
    return new Span(line, column, line, column + token.length(), offset, offset + token.length());
  }
}

package dev.ward.infrastructure.analysis.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.TypeFactsFixture;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks the native JVM type facts against the fixtures the Java sidecar answered. */
class NativeJvmTypesTest {
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/native-types");
  private static final String PATH = "src/main/java/app/User.java";

  @TempDir Path project;

  /** Every Java {@code types.json} fixture must be compared below; a new one fails this test. */
  @Test
  void everyJavaTypesJsonFixtureIsCovered() throws IOException {
    try (Stream<Path> files = Files.walk(Path.of("src/test/resources"))) {
      final List<Path> fixtures =
          files
              .filter(file -> "types.json".equals(file.getFileName().toString()))
              .filter(file -> Files.isDirectory(file.resolveSibling("src/main/java")))
              .toList();
      assertThat(fixtures).containsExactly(FIXTURES.resolve("java/types.json"));
    }
  }

  @Test
  void answersTheSameFactsAsTheFixtureJson() throws IOException {
    final Path source = project.resolve(PATH);
    Files.createDirectories(source.getParent());
    Files.copy(FIXTURES.resolve("java/src/main/java/app/User.java"), source);
    final String text = Files.readString(source);
    final UnitTypeFacts expected = TypeFactsFixture.read(FIXTURES.resolve("java/types.json"));
    final Span equalsCall = span(text, "value.equals(\"x\")");
    final Span toString = span(text, "@Override public String toString() { return name; }");
    final Span read = span(text, "public void read() throws java.io.IOException {}");
    final Span kind = span(text, "\"user\"");
    final Span user =
        span(text, text.substring(text.indexOf("public class User"), text.lastIndexOf('}') + 1));

    final NativeJvmTypes types = new NativeJvmTypes(project);
    try {
      final TypeFacts facts = types.facts(Optional.empty()).orElseThrow();
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
      assertThat(actual.typeOf(kind)).isEqualTo(expected.typeOf(kind));
      assertThat(actual.shapeOf(user)).isEqualTo(expected.shapeOf(user)).isPresent();
    } finally {
      types.close();
    }
  }

  @Test
  void resolvesProjectLibraryAndJdkTypesWithoutAJdkProcess() throws IOException {
    final Path jar = project.resolve("libs/library.jar");
    Files.createDirectories(jar.getParent());
    try (OutputStream out = Files.newOutputStream(jar);
        JarOutputStream jarOut = new JarOutputStream(out);
        InputStream in = Library.class.getResourceAsStream("NativeJvmTypesTest$Library.class")) {
      jarOut.putNextEntry(new JarEntry("lib/Library.class"));
      jarOut.write(rename(in.readAllBytes()));
      jarOut.closeEntry();
    }
    final Path classpath =
        Files.writeString(
            project.resolve("classpath.txt"), jar + "\n" + project.resolve("missing.jar"));
    final Path helper = project.resolve("src/main/java/app/Helper.java");
    Files.createDirectories(helper.getParent());
    Files.writeString(
        helper,
        "package app;\npublic class Helper { public static String name() { return \"h\"; } }\n");
    final String text =
        "package app;\nimport java.util.Objects;\nclass Use {\n  void run() {\n    boolean a = Objects.equals(1, 2);\n    String b = Helper.name();\n    Object c = lib.Library.make();\n  }\n}\n";

    final NativeJvmTypes types = new NativeJvmTypes(project);
    try {
      final TypeFacts facts = types.facts(Optional.of(classpath)).orElseThrow();
      final Span jdk = span(text, "Objects.equals(1, 2)");
      final Span sources = span(text, "Helper.name()");
      final Span library = span(text, "lib.Library.make()");
      final UnitTypeFacts actual =
          facts.factsOf(
              new SourceUnit("src/main/java/app/Use.java", AnalysisLanguage.JAVA, text),
              List.of(jdk, sources, library),
              EnumSet.of(FactKind.TYPE, FactKind.CALL));

      assertThat(actual.callTarget(jdk).orElseThrow().declaringType().name())
          .isEqualTo("java.util.Objects");
      assertThat(actual.typeOf(sources)).map(TypeRef::name).contains("java.lang.String");
      assertThat(actual.callTarget(library).orElseThrow().returnType().name())
          .isEqualTo("java.lang.Object");
    } finally {
      types.close();
    }
  }

  @Test
  void readsTheMembersOfAClassInAClasspathJar() throws IOException {
    final Path jar = project.resolve("libs/library.jar");
    Files.createDirectories(jar.getParent());
    try (OutputStream out = Files.newOutputStream(jar);
        JarOutputStream jarOut = new JarOutputStream(out);
        InputStream in = Library.class.getResourceAsStream("NativeJvmTypesTest$Library.class")) {
      jarOut.putNextEntry(new JarEntry("lib/Library.class"));
      jarOut.write(rename(in.readAllBytes()));
      jarOut.closeEntry();
    }
    final Path classpath = Files.writeString(project.resolve("classpath.txt"), jar.toString());
    final String text =
        "package app;\nclass Use {\n  void run() {\n    lib.Library kept = null;\n    kept.hashCode();\n  }\n}\n";
    final NativeJvmTypes types = new NativeJvmTypes(project);
    try {
      final Span kept = span(text, "kept");
      final UnitTypeFacts actual =
          types
              .facts(Optional.of(classpath))
              .orElseThrow()
              .factsOf(
                  new SourceUnit("src/main/java/app/Use.java", AnalysisLanguage.JAVA, text),
                  List.of(kept),
                  EnumSet.of(FactKind.TYPE, FactKind.MEMBERS));

      final var members = actual.membersOf(kept).orElseThrow();
      assertThat(members.fields()).extracting(field -> field.name()).contains("LIMIT");
      assertThat(members.methods())
          .filteredOn(method -> "legacy".equals(method.name()))
          .singleElement()
          .satisfies(
              method -> assertThat(method.annotations()).containsExactly("java.lang.Deprecated"));
      assertThat(members.ancestors()).extracting(TypeRef::name).containsExactly("java.lang.Object");
    } finally {
      types.close();
    }
  }

  /** Rewrites the class name {@code NativeJvmTypesTest$Library} to {@code lib/Library}. */
  private static byte[] rename(final byte[] bytes) {
    final String from = "dev/ward/infrastructure/analysis/jvm/NativeJvmTypesTest$Library";
    final String to = "lib/Library";
    final String text = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
    final String padded = to + " ".repeat(from.length() - to.length());
    return text.replace(from, padded).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
  }

  private static Span span(final String text, final String token) {
    final int offset = text.lastIndexOf(token);
    final int line = (int) text.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
    final int column = offset - text.lastIndexOf('\n', offset - 1);
    final int end = offset + token.length();
    final int endLine = (int) text.substring(0, end).chars().filter(c -> c == '\n').count() + 1;
    final int endColumn = end - text.lastIndexOf('\n', end - 1);
    return new Span(line, column, endLine, endColumn, offset, end);
  }

  /** A class to put in a jar. */
  public static final class Library {
    private Library() {}

    /** The size. */
    public static final int LIMIT = 3;

    /** Returns an object. */
    public static Object make() {
      return new Object();
    }

    /** An old way. */
    @Deprecated
    public static String legacy() {
      return "";
    }
  }
}

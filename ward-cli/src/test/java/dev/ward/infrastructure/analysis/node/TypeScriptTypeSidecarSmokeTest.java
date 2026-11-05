package dev.ward.infrastructure.analysis.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.ExpressionTypes;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.port.TypeResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the real TypeScript type sidecar on the {@code type-sidecar/typescript} fixture. Skipped
 * unless {@code -Dward.sidecar.typescript} names a directory whose {@code node_modules} installs
 * {@code typescript}; needs {@code node} on the PATH.
 */
class TypeScriptTypeSidecarSmokeTest {
  private static final Path FIXTURE =
      Path.of("src/test/resources/fixtures/type-sidecar/typescript");
  private static final String PATH = "src/app.ts";

  @TempDir Path project;

  @Test
  void typesMemberAccessAndLeavesUnresolvedNamesOut() throws IOException {
    final String installed = System.getProperty("ward.sidecar.typescript");
    assumeTrue(installed != null && !installed.isBlank(), "set -Dward.sidecar.typescript=<dir>");
    copyFixture();
    Files.createSymbolicLink(
        project.resolve("node_modules"), Path.of(installed).resolve("node_modules"));
    final String text = Files.readString(project.resolve(PATH));
    final Span port = span(text, "config.port");
    final Span unresolved = span(text, "nope.value");

    final TypeScriptTypeSidecar sidecar = new TypeScriptTypeSidecar(project, "smoke");
    try {
      final Optional<TypeResolver> resolver = sidecar.start();
      assertThat(resolver).isPresent();
      final ExpressionTypes answer =
          resolver
              .orElseThrow()
              .typesOf(
                  new SourceUnit(PATH, AnalysisLanguage.TYPESCRIPT, text),
                  List.of(port, unresolved));
      assertThat(answer.types()).containsEntry(port, "number").doesNotContainKey(unresolved);
    } finally {
      sidecar.close();
    }
  }

  /** The span of {@code token} in {@code text}, from its first occurrence. */
  private static Span span(final String text, final String token) {
    final int offset = text.indexOf(token);
    final int line = (int) text.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
    final int column = offset - text.lastIndexOf('\n', offset - 1);
    return new Span(line, column, line, column + token.length(), offset, offset + token.length());
  }

  private void copyFixture() throws IOException {
    for (final String name : List.of("tsconfig.json", "package.json", "src/app.ts")) {
      final Path target = project.resolve(name);
      Files.createDirectories(target.getParent());
      Files.copy(FIXTURE.resolve(name), target);
    }
  }
}

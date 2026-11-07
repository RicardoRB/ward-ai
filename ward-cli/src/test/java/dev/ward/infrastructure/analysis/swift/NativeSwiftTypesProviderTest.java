package dev.ward.infrastructure.analysis.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks that the native Swift provider reads a project's files without starting a process. */
final class NativeSwiftTypesProviderTest {

  private static final String BINDING = "pattern_initializer";

  @Test
  void nativeProviderStartsFromProjectFilesWithoutAProcess(@TempDir final Path root)
      throws IOException {
    Files.writeString(root.resolve("Session.swift"), "class Session {}\nlet s = Session()\n");

    final Optional<TypeFacts> facts = new NativeSwiftTypes(root).start();

    assertThat(facts).isPresent();
    final SourceUnit unit =
        new SourceUnit(
            "Session.swift",
            AnalysisLanguage.SWIFT,
            Files.readString(root.resolve("Session.swift")));
    final SyntaxNode binding = findBinding(new AntlrLanguageFrontend().parse(unit).tree());
    assertThat(
            facts
                .orElseThrow()
                .factsOf(unit, List.of(binding.span()), EnumSet.of(FactKind.TYPE))
                .typeOf(binding.span())
                .map(TypeRef::name))
        .contains("Session");
  }

  private static SyntaxNode findBinding(final SyntaxNode node) {
    if (BINDING.equals(node.kind())) {
      return node;
    }
    for (final SyntaxNode child : node.children()) {
      try {
        return findBinding(child);
      } catch (final IllegalStateException notHere) {
        continue;
      }
    }
    throw new IllegalStateException("no pattern_initializer");
  }
}

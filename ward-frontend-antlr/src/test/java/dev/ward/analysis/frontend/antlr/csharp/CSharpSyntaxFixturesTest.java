package dev.ward.analysis.frontend.antlr.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Every file under {@code syntax/csharp/} is a construct of current C#. */
class CSharpSyntaxFixturesTest {
  private static final int MIN_FIXTURES = 13;
  private static final String FIXTURE_ROOT = "/syntax/csharp";

  @DisplayName("the syntax of the current C# release")
  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void parsesWithoutSyntaxDiagnostics(final String name, final Path file) throws IOException {
    final ParseResult result =
        new AntlrLanguageFrontend()
            .parse(new SourceUnit(name, AnalysisLanguage.CSHARP, Files.readString(file)));
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  void everyConstructHasAFixture() {
    assertThat(fixtures().count()).isGreaterThanOrEqualTo(MIN_FIXTURES);
  }

  static Stream<Arguments> fixtures() {
    final List<Arguments> cases = new ArrayList<>();
    try (Stream<Path> children = Files.list(root())) {
      children
          .filter(child -> child.getFileName().toString().endsWith(".cs"))
          .sorted()
          .forEach(child -> cases.add(Arguments.of(child.getFileName().toString(), child)));
    } catch (IOException e) {
      throw new IllegalStateException("cannot list the C# syntax fixtures", e);
    }
    return cases.stream();
  }

  private static Path root() {
    try {
      return Path.of(CSharpSyntaxFixturesTest.class.getResource(FIXTURE_ROOT).toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("bad fixture root", e);
    }
  }
}

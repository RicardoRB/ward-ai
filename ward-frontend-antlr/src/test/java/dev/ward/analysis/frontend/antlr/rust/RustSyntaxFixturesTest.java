package dev.ward.analysis.frontend.antlr.rust;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every file under {@code syntax/rust/} is written from scratch and holds constructs of editions
 * 2021 and 2024. Each must parse with no syntax diagnostic.
 */
class RustSyntaxFixturesTest {
  private static final int MIN_FIXTURES = 8;
  private static final String FIXTURE_ROOT = "/syntax/rust";

  @Test
  @DisplayName("the syntax of the current Rust release")
  void parsesTheSyntaxOfTheCurrentRustRelease() throws IOException {
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    final List<Path> files = fixtures();
    final List<String> diagnostics =
        files.stream().flatMap(file -> diagnostics(frontend, file).stream()).toList();

    assertThat(files).hasSizeGreaterThanOrEqualTo(MIN_FIXTURES);
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void aFileThatDoesNotParseIsReportedWithItsPosition() {
    final ParseResult result =
        new AntlrLanguageFrontend()
            .parse(new SourceUnit("broken.rs", AnalysisLanguage.RUST, "fn main( {\n"));

    assertThat(result.diagnostics()).isNotEmpty();
    assertThat(result.diagnostics().get(0).line()).isEqualTo(1);
  }

  private static List<String> diagnostics(final AntlrLanguageFrontend frontend, final Path file) {
    try {
      final SourceUnit unit =
          new SourceUnit(
              file.getFileName().toString(),
              AnalysisLanguage.RUST,
              Files.readString(file)
          );
      return frontend.parse(unit).diagnostics().stream()
          .map(
              finding ->
                  file.getFileName() + ":" + finding.line() + ":" + finding.column() + " "
                      + finding.message()
          )
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<Path> fixtures() throws IOException {
    try (Stream<Path> children = Files.list(root())) {
      return children.filter(child -> child.toString().endsWith(".rs")).sorted().toList();
    }
  }

  private static Path root() {
    try {
      return Path.of(RustSyntaxFixturesTest.class.getResource(FIXTURE_ROOT).toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("bad fixture root", e);
    }
  }
}

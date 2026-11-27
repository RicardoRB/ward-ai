package dev.ward.infrastructure.analysis.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.types.FactKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The annotations of a package's {@code package-info.java} reach the classes in the package. */
class NativeJvmTypesPackageTest {
  private static final String MARKED = "org.jspecify.annotations.NullMarked";
  private static final String PACKAGE_INFO =
      "@NullMarked\npackage demo;\n\nimport org.jspecify.annotations.NullMarked;\n";
  private static final String CLASS_TEXT = "package demo;\n\nclass Repro {\n}\n";

  @TempDir Path project;

  @Test
  void aClassSeesTheNullMarkedOfItsPackageInfo() throws IOException {
    write("src/main/java/demo/package-info.java", PACKAGE_INFO);

    assertThat(scopesOfRepro()).containsExactly(MARKED);
  }

  @Test
  void aPackageWithoutPackageInfoGivesNoScope() throws IOException {
    write("src/main/java/other/package-info.java", PACKAGE_INFO.replace("demo", "other"));

    assertThat(scopesOfRepro()).isEmpty();
  }

  private List<String> scopesOfRepro() throws IOException {
    write("src/main/java/demo/Repro.java", CLASS_TEXT);
    final int offset = CLASS_TEXT.indexOf("class Repro");
    final Span span = new Span(3, 1, 4, 2, offset, CLASS_TEXT.length() - 1);
    final NativeJvmTypes types = new NativeJvmTypes(project);
    try {
      final TypeFacts facts = types.facts(Optional.empty()).orElseThrow();
      return facts
          .factsOf(
              new SourceUnit("src/main/java/demo/Repro.java", AnalysisLanguage.JAVA, CLASS_TEXT),
              List.of(span),
              EnumSet.of(FactKind.ANNOTATIONS)
          )
          .at(span)
          .scopes();
    } finally {
      types.close();
    }
  }

  private void write(final String relative, final String text) throws IOException {
    final Path file = project.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}

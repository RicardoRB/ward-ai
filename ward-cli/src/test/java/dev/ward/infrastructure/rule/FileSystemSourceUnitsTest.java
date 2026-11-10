package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemSourceUnitsTest {
  private static final String SOURCE = "class A {}\n";

  @TempDir Path root;

  @Test
  void skipsJvmFilesUnderAMavenOrGradleResourceFolder() throws IOException {
    write("src/main/java/A.java");
    write("module/src/test/java/B.java");
    write("module/src/test/resources/fixtures/Broken.java");
    write("src/main/resources/C.kt");

    assertThat(paths(Set.of(AnalysisLanguage.JAVA, AnalysisLanguage.KOTLIN)))
        .containsExactly("module/src/test/java/B.java", "src/main/java/A.java");
  }

  @Test
  void keepsOtherLanguagesInAResourcesFolder() throws IOException {
    write("src/pkg/resources/tool.py");

    assertThat(paths(Set.of(AnalysisLanguage.PYTHON))).containsExactly("src/pkg/resources/tool.py");
  }

  private void write(final String path) throws IOException {
    final Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, SOURCE);
  }

  private List<String> paths(final Set<AnalysisLanguage> languages) {
    return new FileSystemSourceUnits(new ProcessCommandExecutor())
        .read(root, languages).stream().map(SourceUnit::path).toList();
  }
}

package dev.ward.infrastructure.format;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatOption;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.format.FormatTarget;
import dev.ward.domain.process.CommandResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Files that Git ignores, whether alone or inside an ignored folder, are not selected to format.
 */
class FormatIgnoredFilesTest {

  private static final String APP = "src/app.ts";

  @TempDir Path project;

  @Test
  void filesThatGitIgnoresAreNotSelected() throws IOException {
    write(APP, "const a = 1;");
    write("coverage/lcov-report/app.ts", "const a = 1;");

    assertThat(select("coverage/lcov-report/app.ts")).containsExactly(APP);
  }

  @Test
  void filesInAnIgnoredDirectoryAreNotSelected() throws IOException {
    write(APP, "const a = 1;");
    write("dist-out/app.ts", "const a = 1;");

    assertThat(select("dist-out/")).containsExactly(APP);
  }

  private List<String> select(final String ignoredOutput) {
    final FakeCommandExecutor git =
        new FakeCommandExecutor(
            new CommandResult(0, ignoredOutput + "\u0000", "", FakeCommandExecutor.DURATION, null));
    final Map<AnalysisLanguage, List<FormatOption>> declared =
        Map.of(AnalysisLanguage.TYPESCRIPT, EditorConfigTestSupport.TYPESCRIPT_OPTIONS);
    return new FormatFileSelection(new EditorConfigFormatConfiguration(project), git)
        .select(project, declared).stream().map(FormatTarget::relativePath).toList();
  }

  private void write(final String relative, final String text) throws IOException {
    final Path file = project.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text, StandardCharsets.UTF_8);
  }
}

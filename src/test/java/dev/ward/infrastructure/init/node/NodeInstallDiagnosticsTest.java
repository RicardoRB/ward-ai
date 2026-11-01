package dev.ward.infrastructure.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.infrastructure.project.FileSystemPackageManifests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The install's diagnostics name counts, managers and exit codes, never versions or output. */
class NodeInstallDiagnosticsTest {
  @TempDir Path root;

  @Test
  void logsCountsAndExitCodesWithoutVersionsOrOutput() throws IOException {
    Files.writeString(root.resolve("package.json"), "{\n  \"name\": \"shop\"\n}\n");
    final FakeCommandExecutor executor =
        new FakeCommandExecutor(0, "added 3 packages secret-token", "");
    final List<LogRecord> records = new ArrayList<>();
    final Handler handler = handler(records);
    final Logger logger = Logger.getLogger(NodeBuildToolConfigurator.class.getName());
    logger.addHandler(handler);
    try {
      new NodeBuildToolConfigurator(new FileSystemPackageManifests(), executor)
          .apply(
              new Project(
                  root,
                  Language.TYPESCRIPT,
                  BuildTool.NPM,
                  Framework.NONE,
                  false,
                  false,
                  root,
                  Set.of(),
                  "24",
                  TaskRunner.NONE,
                  null),
              List.of(new InitGateOption("prettier", "Prettier", List.of("npx"), true, "ok")));
    } finally {
      logger.removeHandler(handler);
    }

    assertThat(records)
        .extracting(LogRecord::getMessage)
        .containsExactly(
            "event=node-dependencies-added count=1 manager=npm",
            "event=node-install-start manager=npm",
            "event=node-install-complete exit=0");
  }

  private static Handler handler(final List<LogRecord> records) {
    return new Handler() {
      @Override
      public void publish(final LogRecord logRecord) {
        records.add(logRecord);
      }

      @Override
      public void flush() {
        // Records are kept in memory.
      }

      @Override
      public void close() {
        // Nothing to release.
      }
    };
  }
}

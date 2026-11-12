package dev.ward.infrastructure.dependency.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.project.BuildTool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NodeDependencyErrorsTest {
  private static final String PACKAGE_JSON = "package.json";
  private static final String PACKAGE_LOCK = "package-lock.json";
  private static final String LEFT_PAD =
      "{\"name\":\"a\",\"dependencies\":{\"left-pad\":\"1.3.0\"}}";
  private static final String NO_DEPENDENCIES = "{\"name\":\"a\"}";

  private static void write(final Path dir, final String file, final String text)
      throws IOException {
    Files.writeString(dir.resolve(file), text);
  }

  @Test
  void aDirectDependencyMissingFromTheLockfileNamesFileDependencyAndFix(@TempDir final Path dir)
      throws IOException {
    write(dir, PACKAGE_JSON, LEFT_PAD);
    write(dir, PACKAGE_LOCK, "{\"lockfileVersion\":3,\"packages\":{\"\":{}}}");

    assertThatThrownBy(() -> NodeDependencySourceTest.read(dir, BuildTool.NPM))
        .isInstanceOf(DependencyInventoryException.class)
        .hasMessageContaining(PACKAGE_LOCK)
        .hasMessageContaining("left-pad")
        .hasMessageContaining("npm install");
  }

  @Test
  void aManifestWithDependenciesAndNoLockfileNamesTheCommandThatCreatesIt(@TempDir final Path dir)
      throws IOException {
    write(dir, PACKAGE_JSON, LEFT_PAD);

    assertThatThrownBy(() -> NodeDependencySourceTest.read(dir, BuildTool.PNPM))
        .isInstanceOf(DependencyInventoryException.class)
        .hasMessageContaining("pnpm install");
  }

  @Test
  void aManifestWithoutDependenciesNeedsNoLockfile(@TempDir final Path dir) throws IOException {
    write(dir, PACKAGE_JSON, NO_DEPENDENCIES);

    final DependencyInventory inventory = NodeDependencySourceTest.read(dir, BuildTool.NPM);

    assertThat(inventory.entryCount()).isZero();
    assertThat(inventory.modules()).hasSize(1);
  }

  @Test
  void aBinaryBunLockfileAloneSaysHowToCreateTheTextOne(@TempDir final Path dir)
      throws IOException {
    write(dir, PACKAGE_JSON, LEFT_PAD);
    write(dir, "bun.lockb", "binary");

    assertThatThrownBy(() -> NodeDependencySourceTest.read(dir, BuildTool.BUN))
        .hasMessageContaining("bun.lockb")
        .hasMessageContaining("bun install --save-text-lockfile");
  }
}

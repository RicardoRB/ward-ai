package dev.ward.infrastructure.dependency.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.project.BuildTool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NodeDependencyErrorsTest {
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
    write(dir, "package.json", LEFT_PAD);
    write(dir, "package-lock.json", "{\"lockfileVersion\":3,\"packages\":{\"\":{}}}");

    assertThatThrownBy(() -> NodeDependencySourceTest.read(dir, BuildTool.NPM))
        .isInstanceOf(DependencyInventoryException.class)
        .hasMessageContaining("package-lock.json")
        .hasMessageContaining("left-pad")
        .hasMessageContaining("npm install");
  }

  @Test
  void aManifestWithDependenciesAndNoLockfileNamesTheCommandThatCreatesIt(@TempDir final Path dir)
      throws IOException {
    write(dir, "package.json", LEFT_PAD);

    assertThatThrownBy(() -> NodeDependencySourceTest.read(dir, BuildTool.PNPM))
        .isInstanceOf(DependencyInventoryException.class)
        .hasMessageContaining("pnpm install");
  }

  @Test
  void aManifestWithoutDependenciesNeedsNoLockfile(@TempDir final Path dir) throws IOException {
    write(dir, "package.json", NO_DEPENDENCIES);

    final DependencyInventory inventory = NodeDependencySourceTest.read(dir, BuildTool.NPM);

    assertThat(inventory.entryCount()).isZero();
    assertThat(inventory.modules()).hasSize(1);
  }

  @Test
  void aBinaryBunLockfileAloneSaysHowToCreateTheTextOne(@TempDir final Path dir)
      throws IOException {
    write(dir, "package.json", LEFT_PAD);
    write(dir, "bun.lockb", "binary");

    assertThatThrownBy(() -> NodeDependencySourceTest.read(dir, BuildTool.BUN))
        .hasMessageContaining("bun.lockb")
        .hasMessageContaining("bun install --save-text-lockfile");
  }

  @Test
  void anUnknownLockfileVersionNamesTheFileAndTheVersion(@TempDir final Path dir)
      throws IOException {
    write(dir, "package.json", LEFT_PAD);
    write(dir, "package-lock.json", "{\"lockfileVersion\":9,\"packages\":{}}");

    assertThatThrownBy(() -> NodeDependencySourceTest.read(dir, BuildTool.NPM))
        .hasMessageContaining("package-lock.json")
        .hasMessageContaining("lockfileVersion 9");
  }

  @Test
  void aCorruptLockfileNamesTheFile(@TempDir final Path dir) throws IOException {
    write(dir, "package.json", LEFT_PAD);
    write(dir, "package-lock.json", "{\"lockfileVersion\":3,");

    assertThatThrownBy(() -> NodeDependencySourceTest.read(dir, BuildTool.NPM))
        .hasMessageContaining("package-lock.json");
  }

  @Test
  void anEmptyLockfileForDeclaredDependenciesFails(@TempDir final Path dir) throws IOException {
    write(dir, "package.json", LEFT_PAD);
    write(dir, "pnpm-lock.yaml", "lockfileVersion: '9.0'\n");

    assertThatThrownBy(() -> NodeDependencySourceTest.read(dir, BuildTool.PNPM))
        .hasMessageContaining("left-pad");
  }

  @Test
  void aPnpmLineItDoesNotKnowFailsWithItsNumber(@TempDir final Path dir) throws IOException {
    write(dir, "package.json", LEFT_PAD);
    write(dir, "pnpm-lock.yaml", "lockfileVersion: '9.0'\nimporters:\n  .:\n   odd: [\n");

    assertThatThrownBy(() -> NodeDependencySourceTest.read(dir, BuildTool.PNPM))
        .hasMessageContaining("line ");
  }

  @Test
  void aLockfileOfAnotherToolIsIgnored(@TempDir final Path dir) throws IOException {
    write(dir, "package.json", LEFT_PAD);
    write(dir, "package-lock.json", "stale and unreadable");
    write(
        dir,
        "bun.lock",
        "{\"lockfileVersion\":1,\"workspaces\":{\"\":{}},\"packages\":{"
            + "\"left-pad\":[\"left-pad@1.3.0\",\"\",{},\"sha\"],}}");

    final DependencyInventory inventory = NodeDependencySourceTest.read(dir, BuildTool.BUN);

    assertThat(inventory.distinct()).hasSize(1);
    assertThat(inventory.modules().getFirst().dependencies().getFirst().source())
        .isEqualTo("bun.lock");
  }

  @Test
  void aPackageFromGitIsNotARelease(@TempDir final Path dir) throws IOException {
    write(dir, "package.json", LEFT_PAD);
    write(
        dir,
        "package-lock.json",
        "{\"lockfileVersion\":3,\"packages\":{\"\":{},\"node_modules/left-pad\":"
            + "{\"version\":\"1.3.0\",\"resolved\":\"git+ssh://git@github.com/a/left-pad.git#abc\"}}}");

    final Dependency entry =
        NodeDependencySourceTest.read(dir, BuildTool.NPM)
            .modules()
            .getFirst()
            .dependencies()
            .getFirst();

    assertThat(entry.released()).isFalse();
    assertThat(entry.id().purl()).isEqualTo("pkg:npm/left-pad@1.3.0");
  }

  @Test
  void jsoncDropsCommentsAndTrailingCommasButKeepsStrings() {
    assertThat(
            Jsonc.toJson("{\"a\": \"x // y,\", // note\n \"b\": [1, 2,], /* c */ }")
                .replaceAll("\\s", ""))
        .isEqualTo("{\"a\":\"x//y,\",\"b\":[1,2]}");
  }
}

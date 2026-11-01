package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The formatter's inputs: Package.swift and the top-level directories holding Swift sources. */
class FileSystemSwiftSourceDirectoriesTest {
  @TempDir Path root;

  @Test
  void packageLayoutListsPackageSwiftThenSourceDirectories() throws IOException {
    write("Package.swift", "// swift-tools-version: 6.0\n");
    write("Sources/App/Cart.swift", "struct Cart {}\n");
    write("Tests/AppTests/CartTests.swift", "import Testing\n");

    assertThat(new FileSystemSwiftSourceDirectories().of(root))
        .containsExactly("Package.swift", "Sources", "Tests");
  }

  @Test
  void buildAndDependencyDirectoriesAreLeftOut() throws IOException {
    write("Sources/App.swift", "struct App {}\n");
    write(".build/checkouts/Lib/Lib.swift", "struct Lib {}\n");
    write("Pods/Pod/Pod.swift", "struct Pod {}\n");
    write(".hidden/Hidden.swift", "struct Hidden {}\n");
    Files.createDirectories(root.resolve("Empty"));

    assertThat(new FileSystemSwiftSourceDirectories().of(root)).containsExactly("Sources");
  }

  private void write(final String name, final String content) throws IOException {
    Files.createDirectories(root.resolve(name).getParent());
    Files.writeString(root.resolve(name), content);
  }
}

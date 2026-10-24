package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.PackageManifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The package.json facts init reads through the manifest port. */
class FileSystemPackageManifestsTest {
  @TempDir Path dir;

  private final FileSystemPackageManifests manifests = new FileSystemPackageManifests();

  @Test
  void readsDeclaredDependenciesTypeAndPackageManager() throws IOException {
    Files.writeString(
        dir.resolve("package.json"),
        """
        {
          "type": "module",
          "packageManager": "pnpm@9.12.0",
          "dependencies": {"zod": "^3.23.0"},
          "devDependencies": {"typescript": "~5.9.3", "prettier": "^3.3.0"}
        }
        """);

    final PackageManifest manifest = manifests.read(dir);

    assertThat(manifest.dependencies())
        .containsEntry("zod", "^3.23.0")
        .containsEntry("typescript", "~5.9.3")
        .containsEntry("prettier", "^3.3.0");
    assertThat(manifest.declares("zod")).isTrue();
    assertThat(manifest.declares("eslint")).isFalse();
    assertThat(manifest.module()).isTrue();
    assertThat(manifest.packageManager()).isEqualTo("pnpm@9.12.0");
  }

  @Test
  void projectWithoutTypeIsNotAModule() throws IOException {
    Files.writeString(dir.resolve("package.json"), "{\"name\": \"shop\"}");

    final PackageManifest manifest = manifests.read(dir);

    assertThat(manifest.module()).isFalse();
    assertThat(manifest.type()).isNull();
    assertThat(manifest.packageManager()).isNull();
  }

  @Test
  void malformedOrMissingManifestIsEmpty() throws IOException {
    assertThat(manifests.read(dir)).isEqualTo(PackageManifest.EMPTY);

    Files.writeString(dir.resolve("package.json"), "{\"devDependencies\": ");

    assertThat(manifests.read(dir)).isEqualTo(PackageManifest.EMPTY);
  }
}

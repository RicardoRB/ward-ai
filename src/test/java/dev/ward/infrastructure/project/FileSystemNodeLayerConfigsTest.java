package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The base files and Ward-owned roots of a layer, which the Node config lookups report. */
class FileSystemNodeLayerConfigsTest {
  private static final String ESLINT = "eslint";

  private final FileSystemNodeToolConfigs configs = new FileSystemNodeToolConfigs();

  @TempDir Path root;

  @Test
  void baseFileOfALayerIsFound() throws IOException {
    Files.writeString(root.resolve("eslint.base.mjs"), "export default [];\n");
    Files.writeString(root.resolve("biome.base.json"), "{}\n");

    assertThat(configs.baseConfig(root, ESLINT)).contains(root.resolve("eslint.base.mjs"));
    assertThat(configs.baseConfig(root, "biome")).contains(root.resolve("biome.base.json"));
  }

  @Test
  void wardRootWithItsBaseReadsAsALayer() throws IOException {
    Files.writeString(
        root.resolve("eslint.config.mjs"), "// managed-by: ward preset=strict\nimport base;\n");
    Files.writeString(root.resolve("eslint.base.mjs"), "export default [];\n");

    assertThat(configs.wardConfig(root, ESLINT)).contains(root.resolve("eslint.config.mjs"));
    assertThat(configs.baseConfig(root, ESLINT)).contains(root.resolve("eslint.base.mjs"));
    assertThat(configs.userConfig(root, ESLINT)).isEmpty();
  }

  @Test
  void aUserConfigIsNotABase() throws IOException {
    Files.writeString(root.resolve("eslint.config.mjs"), "export default [];\n");

    assertThat(configs.userConfig(root, ESLINT)).contains(root.resolve("eslint.config.mjs"));
    assertThat(configs.baseConfig(root, ESLINT)).isEmpty();
    assertThat(configs.wardConfig(root, ESLINT)).isEmpty();
  }
}

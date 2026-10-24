package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FileSystemNodeToolConfigsTest {
  private final FileSystemNodeToolConfigs configs = new FileSystemNodeToolConfigs();

  @TempDir Path root;

  @ParameterizedTest
  @CsvSource({
    "vitest, vitest.config.ts",
    "vitest, vite.config.ts",
    "jest, jest.config.js",
    "stryker, stryker.conf.json",
    "stryker, .stryker.config.mjs",
    "stryker, stryker.config.mjs",
    "eslint, eslint.config.js",
    "eslint, .eslintrc.json"
  })
  void userConfigOfTheGatesToolIsFound(final String gate, final String file) throws IOException {
    Files.writeString(root.resolve(file), "export default {};\n");

    assertThat(configs.userConfig(root, gate)).contains(root.resolve(file));
  }

  @Test
  void wardOwnedConfigsDoNotCount() throws IOException {
    Files.writeString(root.resolve("vitest.config.mjs"), "// managed-by: ward preset=strict\n");
    Files.writeString(root.resolve("bunfig.toml"), "# managed-by: ward\n");

    assertThat(configs.userConfig(root, "vitest")).isEmpty();
  }

  @Test
  void anotherToolsConfigDoesNotCount() throws IOException {
    Files.writeString(root.resolve("jest.config.js"), "module.exports = {};\n");

    assertThat(configs.userConfig(root, "vitest")).isEmpty();
    assertThat(configs.userConfig(root, "prettier")).isEmpty();
  }

  @Test
  void jestKeyInPackageJsonIsJestsConfig() throws IOException {
    Files.writeString(root.resolve("package.json"), "{\"jest\": {\"testEnvironment\": \"node\"}}");

    assertThat(configs.userConfig(root, "jest")).contains(root.resolve("package.json"));
    assertThat(configs.userConfig(root, "vitest")).isEmpty();
  }

  @Test
  void packageJsonWithoutJestKeyIsNoConfig() throws IOException {
    Files.writeString(root.resolve("package.json"), "{\"name\": \"app\"}");

    assertThat(configs.userConfig(root, "jest")).isEmpty();
  }

  @Test
  void missingRootHasNoConfig() {
    assertThat(configs.userConfig(root.resolve("absent"), "eslint")).isEmpty();
  }
}

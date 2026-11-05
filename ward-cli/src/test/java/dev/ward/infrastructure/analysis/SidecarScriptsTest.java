package dev.ward.infrastructure.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SidecarScriptsTest {
  @TempDir Path root;

  @Test
  void writesTheBundledScriptUnderTheVersionedCache() throws Exception {
    final Path script = SidecarScripts.extract(root, "9.9.9", "ward-ts-types.mjs").orElseThrow();

    assertThat(script).isEqualTo(root.resolve(".ward/cache/sidecars/9.9.9/ward-ts-types.mjs"));
    assertThat(Files.readString(script)).contains("createRequire");
  }

  @Test
  void aMissingScriptIsNotExtracted() {
    assertThat(SidecarScripts.extract(root, "9.9.9", "missing.mjs")).isEmpty();
  }
}

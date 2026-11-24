package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The owner of a body and the module that a relative specifier names are one string. */
class NodeModuleNamesTest {
  @Test
  void aFileIsNamedByItsPathWithoutTheScriptExtension() {
    assertThat(NodeModuleNames.of("lib/files.ts")).isEqualTo("lib/files");
    assertThat(NodeModuleNames.of("lib/files.mjs")).isEqualTo("lib/files");
    assertThat(NodeModuleNames.of("lib/util.service.tsx")).isEqualTo("lib/util.service");
  }

  @Test
  void anIndexFileIsNamedByItsFolder() {
    assertThat(NodeModuleNames.of("lib/files/index.ts")).isEqualTo("lib/files");
    assertThat(NodeModuleNames.of("index.js")).isEmpty();
  }

  @Test
  void aRelativeSpecifierResolvesAgainstTheFolderOfTheFile() {
    assertThat(NodeModuleNames.resolve("routes", "../lib/files")).isEqualTo("lib/files");
    assertThat(NodeModuleNames.resolve("routes", "./a.js")).isEqualTo("routes/a");
    assertThat(NodeModuleNames.resolve("", "./a")).isEqualTo("a");
    assertThat(NodeModuleNames.resolve("routes/deep", "../../lib")).isEqualTo("lib");
  }

  @Test
  void aSpecifierThatIsNotRelativeOrLeavesTheProjectIsKeptAsWritten() {
    assertThat(NodeModuleNames.resolve("routes", "express")).isEqualTo("express");
    assertThat(NodeModuleNames.resolve("routes", "@scope/pkg/x")).isEqualTo("@scope/pkg/x");
    assertThat(NodeModuleNames.resolve("routes", "../../outside")).isEqualTo("../../outside");
  }

  @Test
  void theFolderOfAFileIsEmptyAtTheRoot() {
    assertThat(NodeModuleNames.directoryOf("a.ts")).isEmpty();
    assertThat(NodeModuleNames.directoryOf("routes/a.ts")).isEqualTo("routes");
  }
}

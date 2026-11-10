package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The modules that import their own dotted path, and the files that cannot tell. */
final class ImportSelfCheckTest {

  private static final ImportSelfCheck CHECK = new ImportSelfCheck("m");

  @Test
  void anImportOfTheOwnDottedPathIsReported() {
    assertThat(CHECK.check(ParsedPython.tree("import pkg.mod"), new SourceFile("pkg/mod.py", "")))
        .hasSize(1);
    assertThat(
            CHECK.check(
                ParsedPython.tree("from pkg.mod import x"), new SourceFile("pkg/mod.py", "")))
        .hasSize(1);
  }

  @Test
  void aPackageInitImportingItsOwnNameIsReported() {
    assertThat(CHECK.check(ParsedPython.tree("import pkg"), new SourceFile("pkg/__init__.py", "")))
        .hasSize(1);
  }

  @Test
  void otherImportsAndFilesWithoutAPathAreNotReported() {
    assertThat(CHECK.check(ParsedPython.tree("import os"), new SourceFile("pkg/mod.py", "")))
        .isEmpty();
    assertThat(CHECK.check(ParsedPython.tree("import pkg.mod"), SourceFile.UNKNOWN)).isEmpty();
  }
}

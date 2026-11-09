package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.node.NodeFileFacts;
import org.junit.jupiter.api.Test;

/** The package name a bare specifier names, as the dependency checks read it. */
final class NodeFileFactsTest {

  @Test
  void namesThePackageOfAScopedOrPlainSpecifier() {
    assertThat(NodeFileFacts.packageName("lodash/fp")).isEqualTo("lodash");
    assertThat(NodeFileFacts.packageName("@scope/pkg/sub/file")).isEqualTo("@scope/pkg");
    assertThat(NodeFileFacts.packageName("@scope")).isEmpty();
  }

  @Test
  void namesNothingForRelativeSubpathAndBuiltInSpecifiers() {
    assertThat(NodeFileFacts.packageName("./lib")).isEmpty();
    assertThat(NodeFileFacts.packageName("../lib")).isEmpty();
    assertThat(NodeFileFacts.packageName("/abs")).isEmpty();
    assertThat(NodeFileFacts.packageName("#internal")).isEmpty();
    assertThat(NodeFileFacts.packageName("node:fs")).isEmpty();
    assertThat(NodeFileFacts.packageName("path")).isEmpty();
  }
}

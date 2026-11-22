package dev.ward.analysis.application.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CSharpRuleFilesTest {
  @Test
  void everyListedRuleFileIsPackagedInTheModule() {
    final List<String> files = new CSharpRuleFiles().ruleFiles();

    assertThat(files).isNotEmpty().doesNotHaveDuplicates().isSorted();
    files.forEach(file -> assertThat(CSharpRuleFiles.class.getResource("/" + file)).isNotNull());
  }
}

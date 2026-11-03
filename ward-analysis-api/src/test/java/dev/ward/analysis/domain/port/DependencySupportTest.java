package dev.ward.analysis.domain.port;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.List;
import org.junit.jupiter.api.Test;

class DependencySupportTest {
  private static final FileSummarizer SUMMARIZER = (unit, tree) -> null;
  private static final DependencyResolver RESOLVER = summaries -> List.of();

  @Test
  void requiresEveryAdapter() {
    assertThatThrownBy(() -> new DependencySupport(null, SUMMARIZER, RESOLVER))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new DependencySupport(AnalysisLanguage.JAVA, null, RESOLVER))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new DependencySupport(AnalysisLanguage.JAVA, SUMMARIZER, null))
        .isInstanceOf(NullPointerException.class);
  }
}

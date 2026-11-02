package dev.ward.analysis.domain.port;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnalysisPortsTest {

  @Test
  void fakeFrontendBuildsTreeWithoutAdapter() {
    final LanguageFrontend frontend =
        unit ->
            new SyntaxNode(
                "file",
                new Span(1, 1, 1, 1, 0, 0),
                Map.of("path", unit.path()),
                java.util.List.of());

    final SyntaxNode root = frontend.parse(new SourceUnit("a.ts", AnalysisLanguage.TYPESCRIPT, ""));

    assertThat(root.fields()).containsEntry("path", "a.ts");
  }

  @Test
  void inMemoryCacheReturnsWhatWasWritten() {
    final AnalysisCache cache = new InMemoryCache();

    assertThat(cache.read("k")).isEmpty();
    cache.write("k", "v1");
    cache.write("k", "v2");

    assertThat(cache.read("k")).contains("v2");
  }

  /** Test fixture: a map-backed cache. */
  private static final class InMemoryCache implements AnalysisCache {
    private final Map<String, String> values = new HashMap<>();

    @Override
    public Optional<String> read(final String key) {
      return Optional.ofNullable(values.get(key));
    }

    @Override
    public void write(final String key, final String value) {
      values.put(key, value);
    }
  }
}

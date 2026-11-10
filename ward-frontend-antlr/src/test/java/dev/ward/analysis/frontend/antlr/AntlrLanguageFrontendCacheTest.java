package dev.ward.analysis.frontend.antlr;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AntlrLanguageFrontendCacheTest {

  private static final int CACHE_BOUND = 64;

  @Test
  void aRecentlyParsedUnitIsReturnedFromTheCache() {
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    final SourceUnit unit = unit(0);

    assertThat(frontend.parse(unit)).isSameAs(frontend.parse(unit));
  }

  @Test
  void aUnitBeyondTheCacheBoundIsParsedAgain() {
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    final ParseResult first = frontend.parse(unit(0));
    // As many distinct units as the cache keeps evict the first one.
    IntStream.rangeClosed(1, CACHE_BOUND).forEach(i -> frontend.parse(unit(i)));

    assertThat(frontend.parse(unit(0))).isNotSameAs(first);
  }

  private static SourceUnit unit(final int index) {
    final String name = "U" + index;
    return new SourceUnit(name + ".java", AnalysisLanguage.JAVA, "class " + name + " {}");
  }
}

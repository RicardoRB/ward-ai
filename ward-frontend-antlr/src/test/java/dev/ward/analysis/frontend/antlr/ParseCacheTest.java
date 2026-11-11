package dev.ward.analysis.frontend.antlr;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import org.junit.jupiter.api.Test;

class ParseCacheTest {

  private static final int TEXT_LENGTH = 10_000;
  private static final int UNITS_THAT_FIT = 3;
  private static final int MANY = 100;

  /** Three units of {@link #TEXT_LENGTH} plus their entry cost fit; a fourth does not. */
  private static final long BUDGET = (long) UNITS_THAT_FIT * (TEXT_LENGTH + 1024) + 1;

  private static final ParseResult RESULT =
      new AntlrLanguageFrontend()
          .parse(new SourceUnit("A.java", AnalysisLanguage.JAVA, "class A {}"));

  @Test
  void theWeightBoundDropsTheLeastRecentlyUsedUnit() {
    final ParseCache cache = new ParseCache(BUDGET);
    put(cache, unit(0), RESULT);
    put(cache, unit(1), RESULT);
    put(cache, unit(2), RESULT);
    cache.get(unit(0));

    put(cache, unit(UNITS_THAT_FIT), RESULT);

    assertThat(cache.get(unit(1))).isNull();
    assertThat(cache.get(unit(0))).isSameAs(RESULT);
    assertThat(cache.get(unit(2))).isSameAs(RESULT);
    assertThat(cache.get(unit(UNITS_THAT_FIT))).isSameAs(RESULT);
  }

  @Test
  void manyUnitsNeverHoldMoreThanTheBudget() {
    final ParseCache cache = new ParseCache(BUDGET);
    for (int i = 0; i < MANY; i++) {
      put(cache, unit(i), RESULT);
    }

    long kept = 0;
    for (int i = 0; i < MANY; i++) {
      kept += cache.get(unit(i)) == null ? 0 : 1;
    }
    assertThat(kept).isEqualTo(UNITS_THAT_FIT);
  }

  @Test
  void aUnitHeavierThanTheBudgetIsStillKeptAsTheNewestEntry() {
    final ParseCache cache = new ParseCache(1);
    put(cache, unit(0), RESULT);
    put(cache, unit(1), RESULT);

    assertThat(cache.get(unit(0))).isNull();
    assertThat(cache.get(unit(1))).isSameAs(RESULT);
  }

  /** The parser of a cache miss returns the result that the test chose. */
  private static void put(final ParseCache cache, final SourceUnit unit, final ParseResult result) {
    cache.parse(unit, key -> result);
  }

  private static SourceUnit unit(final int index) {
    return new SourceUnit("U" + index + ".java", AnalysisLanguage.JAVA, "x".repeat(TEXT_LENGTH));
  }
}

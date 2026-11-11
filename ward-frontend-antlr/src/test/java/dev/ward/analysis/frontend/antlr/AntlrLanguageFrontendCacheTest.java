package dev.ward.analysis.frontend.antlr;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class AntlrLanguageFrontendCacheTest {

  private static final int THREADS = 8;

  @Test
  void twoPassesOverTheSameUnitShareOneParse() {
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    final SourceUnit unit = unit("class A {}");

    final ParseResult dependencies = frontend.parse(unit);
    // The next pass builds its own unit value from the same file.
    final ParseResult rules = frontend.parse(unit("class A {}"));

    assertThat(rules).isSameAs(dependencies);
  }

  @Test
  void threadsAskingForTheSameUnitShareOneParse() throws InterruptedException, ExecutionException {
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    final CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
      final List<Future<ParseResult>> results = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return frontend.parse(unit("class A {}"));
                }));
      }
      start.countDown();
      final ParseResult first = results.get(0).get();
      for (final Future<ParseResult> result : results) {
        assertThat(result.get()).isSameAs(first);
      }
    }
  }

  @Test
  void aUnitWhoseTextChangedIsParsedAgain() {
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    final ParseResult before = frontend.parse(unit("class A {}"));

    final ParseResult after = frontend.parse(unit("class A { int x; }"));

    assertThat(after).isNotSameAs(before);
  }

  private static SourceUnit unit(final String text) {
    return new SourceUnit("A.java", AnalysisLanguage.JAVA, text);
  }
}

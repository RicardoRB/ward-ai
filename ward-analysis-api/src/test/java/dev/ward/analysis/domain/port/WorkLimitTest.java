package dev.ward.analysis.domain.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class WorkLimitTest {
  private static final int ITEMS = 200;
  private static final int THREADS = 4;
  private static final int DEEP = 20_000;
  private static final int FAILING = 1;
  private static final List<Integer> FEW = List.of(10, 20, 30);

  @Test
  void resultsKeepTheOrderOfTheItemsWithAnyLimit() {
    final List<Integer> items = IntStream.range(0, ITEMS).boxed().toList();

    assertThat(new WorkLimit(1).map(items, item -> item * 2))
        .isEqualTo(new WorkLimit(THREADS).map(items, item -> item * 2))
        .hasSize(ITEMS)
        .startsWith(0, 2);
  }

  @Test
  void noMoreThreadsWorkAtOnceThanTheLimit() {
    final WorkLimit limit = new WorkLimit(THREADS);
    final AtomicInteger working = new AtomicInteger();
    final AtomicInteger most = new AtomicInteger();

    limit.map(
        IntStream.range(0, ITEMS).boxed().toList(),
        item -> {
          most.accumulateAndGet(working.incrementAndGet(), Math::max);
          Thread.onSpinWait();
          return working.decrementAndGet();
        }
    );

    assertThat(most.get()).isBetween(1, THREADS);
  }

  @Test
  void aCheckThatHoldsAPermitCountsAgainstTheItemsOfAnotherCheck() throws InterruptedException {
    final WorkLimit limit = new WorkLimit(2);
    final AtomicInteger working = new AtomicInteger();
    final AtomicInteger most = new AtomicInteger();
    final Runnable check =
        () ->
        limit.holding(
            () ->
                limit.map(
                    IntStream.range(0, ITEMS).boxed().toList(),
                    item -> {
                      most.accumulateAndGet(working.incrementAndGet(), Math::max);
                      Thread.onSpinWait();
                      return working.decrementAndGet();
                    }
                )
        );
    final Thread first = new Thread(check);
    final Thread second = new Thread(check);

    first.start();
    second.start();
    first.join();
    second.join();

    assertThat(most.get()).isBetween(1, 2);
  }

  @Test
  void aCallerThatHoldsTheOnlyPermitLendsItToItsItems() {
    final WorkLimit limit = new WorkLimit(1);

    final List<Integer> results = limit.holding(() -> limit.map(FEW, item -> item));

    assertThat(results).isEqualTo(FEW);
    assertThat(limit.holding(() -> "still usable")).isEqualTo("still usable");
  }

  @Test
  void itemsRunOnAnalysisThreadsNeverOnTheCaller() {
    final Set<Thread> threads = ConcurrentHashMap.newKeySet();

    new WorkLimit(1).map(FEW, item -> threads.add(Thread.currentThread()));

    assertThat(threads).hasSize(1).doesNotContain(Thread.currentThread());
  }

  @Test
  void anAnalysisThreadHasRoomForDeepRecursionWithAnyLimit() {
    assertThat(new WorkLimit(1).map(List.of(DEEP), WorkLimitTest::depth)).containsExactly(DEEP);
    assertThat(new WorkLimit(THREADS).map(List.of(DEEP, DEEP), WorkLimitTest::depth))
        .containsExactly(DEEP, DEEP);
  }

  @Test
  void aFailingItemEndsTheCallWithItsFailureAndFreesThePermits() {
    final WorkLimit limit = new WorkLimit(THREADS);

    assertThatThrownBy(
        () ->
            limit.map(
                IntStream.range(0, ITEMS).boxed().toList(),
                item -> {
                  if (item == FAILING) {
                    throw new IllegalStateException("item 1 failed");
                  }
                  return item;
                }
            )
    )
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("item 1 failed");
    assertThat(limit.map(List.of(1), item -> item)).containsExactly(1);
  }

  @Test
  void noItemsNeedNoThread() {
    assertThat(new WorkLimit(1).map(List.<Integer> of(), item -> item)).isEmpty();
  }

  @Test
  void aLimitBelowOneIsRejected() {
    assertThatThrownBy(() -> new WorkLimit(0)).isInstanceOf(IllegalArgumentException.class);
    assertThat(WorkLimit.serial().threads()).isEqualTo(1);
  }

  private static int depth(final int levels) {
    return levels == 0 ? 0 : 1 + depth(levels - 1);
  }
}

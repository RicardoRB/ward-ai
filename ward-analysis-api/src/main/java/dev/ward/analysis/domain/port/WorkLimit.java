package dev.ward.analysis.domain.port;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * How many threads may work at once in one run, counting running checks and files under analysis
 * together. A check runs {@linkplain #holding(Supplier) holding} one permit; the items of {@link
 * #map(List, Function)} hold one each, on analysis threads that all have the same stack size, so
 * the result of an item does not depend on the limit or on the thread that took it.
 */
public final class WorkLimit {
  /**
   * The stack of an analysis thread: the usual stack of the main thread of a native binary, and
   * above the default of a JVM thread, so no file that the calling thread could analyze runs out of
   * stack on an analysis thread.
   */
  private static final long STACK_BYTES = 8L * 1024 * 1024;

  private static final int MINIMUM = 1;

  /** How long a thread waits for a permit before it looks again whether an item is left. */
  private static final long POLL_MILLIS = 20L;

  private final int threads;
  private final Semaphore permits;
  private final Set<Thread> holders = ConcurrentHashMap.newKeySet();

  /**
   * Creates a limit of {@code threads} working threads.
   *
   * @throws IllegalArgumentException if {@code threads} is below 1
   */
  public WorkLimit(final int threads) {
    if (threads < MINIMUM) {
      throw new IllegalArgumentException("the limit must be at least 1: " + threads);
    }
    this.threads = threads;
    this.permits = new Semaphore(threads, true);
  }

  /** Returns a limit of one working thread. */
  public static WorkLimit serial() {
    return new WorkLimit(MINIMUM);
  }

  /** Returns how many threads may work at once. */
  public int threads() {
    return threads;
  }

  /** Runs {@code task} on the calling thread while it holds one permit, and returns its result. */
  public <R> R holding(final Supplier<R> task) {
    final Thread current = Thread.currentThread();
    permits.acquireUninterruptibly();
    holders.add(current);
    try {
      return task.get();
    } finally {
      holders.remove(current);
      permits.release();
    }
  }

  /**
   * Applies {@code work} to every item on analysis threads, each item holding one permit, and
   * returns the results in the order of {@code items}. A caller that holds a permit lends it to the
   * items while it waits. A failure of {@code work} ends the call with that failure.
   */
  public <T, R> List<R> map(final List<T> items, final Function<T, R> work) {
    if (items.isEmpty()) {
      return List.of();
    }
    final Thread current = Thread.currentThread();
    final boolean lent = holders.remove(current);
    if (lent) {
      permits.release();
    }
    try {
      return onAnalysisThreads(List.copyOf(items), work);
    } finally {
      if (lent) {
        permits.acquireUninterruptibly();
        holders.add(current);
      }
    }
  }

  private <T, R> List<R> onAnalysisThreads(final List<T> items, final Function<T, R> work) {
    final int workers = Math.min(threads, items.size());
    final AtomicReferenceArray<R> results = new AtomicReferenceArray<>(items.size());
    final AtomicInteger next = new AtomicInteger();
    try (ExecutorService pool =
        Executors.newFixedThreadPool(
            workers,
            Thread.ofPlatform().name("ward-analysis-", 1).stackSize(STACK_BYTES).factory()
        )) {
      IntStream.range(0, workers)
          .mapToObj(
              worker ->
                  CompletableFuture.runAsync(() -> drain(items, work, next, results), pool)
          )
          .toList()
          .forEach(WorkLimit::await);
    }
    return IntStream.range(0, items.size()).mapToObj(results::get).toList();
  }

  /** Takes items until none is left; waits for a permit before each one. */
  private <T, R> void drain(
      final List<T> items,
      final Function<T,
      R> work,
      final AtomicInteger next,
      final AtomicReferenceArray<R> results
  ) {
    try {
      while (next.get() < items.size()) {
        if (permits.tryAcquire(POLL_MILLIS, TimeUnit.MILLISECONDS)) {
          applyNext(items, work, next, results);
        }
      }
    } catch (final InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("the analysis was interrupted", interrupted);
    }
  }

  /** Applies the work to the next item, with the permit the caller took, and gives it back. */
  private <T, R> void applyNext(
      final List<T> items,
      final Function<T,
      R> work,
      final AtomicInteger next,
      final AtomicReferenceArray<R> results
  ) {
    boolean finished = false;
    try {
      final int index = next.getAndIncrement();
      if (index < items.size()) {
        results.set(index, work.apply(items.get(index)));
      }
      finished = true;
    } finally {
      if (!finished) {
        // A failed item ends the call: the other threads take no further item.
        next.set(items.size());
      }
      permits.release();
    }
  }

  private static void await(final CompletableFuture<Void> worker) {
    try {
      worker.join();
    } catch (final CompletionException exception) {
      if (exception.getCause() instanceof RuntimeException cause) {
        throw cause;
      }
      if (exception.getCause() instanceof Error cause) {
        throw cause;
      }
      throw exception;
    }
  }
}

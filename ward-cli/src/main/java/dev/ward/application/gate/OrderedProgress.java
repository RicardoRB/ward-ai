package dev.ward.application.gate;

import dev.ward.domain.gate.GateResult;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Delivers the progress of gates that may run at the same time as if they ran one after another:
 * the listener hears one gate at a time, in the order the runner walks them. A step of a gate that
 * is not the one being reported is dropped.
 */
final class OrderedProgress {
  private static final int NONE = -1;

  private final CheckProgressListener listener;
  private final ReentrantLock lock = new ReentrantLock();
  private final AtomicInteger head = new AtomicInteger(NONE);

  OrderedProgress(final CheckProgressListener listener) {
    this.listener = listener;
  }

  /** Reports that the gate at {@code index} is the one the runner now waits for. */
  void started(final int index, final String name) {
    lock.lock();
    try {
      head.set(index);
      listener.gateStarted(name);
    } finally {
      lock.unlock();
    }
  }

  /** Reports {@code step} of the gate at {@code index} when it is the reported gate. */
  void step(final int index, final String name, final String step) {
    lock.lock();
    try {
      if (head.get() == index) {
        listener.gateStep(name, step);
      }
    } finally {
      lock.unlock();
    }
  }

  /** Reports the result of the reported gate, or of a gate that did not run. */
  void finished(final GateResult result) {
    lock.lock();
    try {
      head.set(NONE);
      listener.gateFinished(result);
    } finally {
      lock.unlock();
    }
  }
}

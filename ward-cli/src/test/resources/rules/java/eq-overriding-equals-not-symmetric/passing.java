class Special extends Base {
  public boolean equals(Object other) {
    return other != null && getClass() == other.getClass() && super.equals(other);
  }
}

class Counter extends Number {
  private long count;

  public boolean equals(Object other) {
    return other instanceof Counter && ((Counter) other).count == count;
  }
}

class Worker extends Thread {
  public boolean equals(Object other) {
    return other instanceof Worker;
  }
}

class Semaphores {
  private static class Acquirer extends Thread {}

  private static final class EqualAcquirer extends Acquirer {
    @Override
    public boolean equals(final Object other) {
      return other instanceof EqualAcquirer;
    }
  }
}

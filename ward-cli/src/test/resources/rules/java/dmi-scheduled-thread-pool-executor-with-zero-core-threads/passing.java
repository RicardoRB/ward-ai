final class Pools {
  Object pool() {
    return new ScheduledThreadPoolExecutor(1);
  }
}

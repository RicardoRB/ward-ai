@ThreadSafe
final class T {
  int count;
  private final int ok = 1;
  private volatile int v;
  @GuardedBy("this") int guarded;
}

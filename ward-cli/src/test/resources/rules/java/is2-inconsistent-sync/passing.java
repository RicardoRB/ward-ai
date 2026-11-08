class Getter {
  private int v;

  public synchronized void set(int x) {
    v = x;
  }

  public synchronized void add() {
    v++;
  }

  public int get() {
    return v;
  }
}

class Allowed {
  private int a;
  private volatile int b;
  private final int c = 1;
  public int d;
  private static int e;
  private int f;

  Allowed() {
    a = 1;
    f = 2;
  }

  public synchronized void work() {
    a++;
    b++;
    e++;
    helper();
  }

  private void helper() {
    a--;
  }

  public void other() {
    Runnable r = () -> f++;
  }

  public int unrelated() {
    return c + d + b;
  }
}

class Mostly {
  private int m;

  public synchronized void set() {
    m = 1;
  }

  public void a() {
    m = 2;
  }

  public void b() {
    m = 3;
  }

  public void c() {
    m = m + 4;
  }
}

class Clash {
  private int n;

  public synchronized void set() {
    n = 1;
  }

  public synchronized int a(int x) {
    return n + x;
  }

  public int a(String x) {
    n = 5;
    return n;
  }
}

class Locks {
  private final Object lock = new Object();
  private int k;

  public void a() {
    synchronized (lock) {
      k++;
    }
  }

  public void b() {
    k--;
  }
}

@NotThreadSafe
class Annotated {
  private int n;

  public synchronized void a() {
    n = 1;
  }

  public synchronized int b() {
    return n;
  }

  public void c() {
    n = 2;
  }

  public int d() {
    return n;
  }
}

class Page extends HttpServlet {
  private int n;

  public synchronized void a() {
    n = 1;
  }

  public synchronized int b() {
    return n;
  }

  public void c() {
    n = 2;
  }

  public int d() {
    return n;
  }
}

class OnCall {
  private int n;

  Object lock() {
    return this;
  }

  public void a() {
    synchronized (lock()) {
      n = 1;
    }
  }

  public int b() {
    synchronized (lock()) {
      return n;
    }
  }

  public void c() {
    n = 2;
  }
}

class Lifecycle {
  private int n;

  public synchronized void a() {
    n = 1;
  }

  public synchronized int b() {
    return n;
  }

  public void close() {
    n = 0;
  }

  public void finalize() {
    n = n + 1;
  }

  public static void s() {
    new Lifecycle().n = 3;
  }

  public void ret() {
    try {
      a();
    } finally {
      n = 4;
    }
  }
}

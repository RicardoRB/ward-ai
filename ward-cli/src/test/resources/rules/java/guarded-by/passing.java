class Passing {
  @GuardedBy("this")
  private int balance;

  Passing() {
    balance = 0;
  }

  synchronized void safe() {
    balance++;
  }

  void block() {
    synchronized (this) {
      balance++;
    }
  }

  void shadow(int balance) {
    balance++;
  }
}

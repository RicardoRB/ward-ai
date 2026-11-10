class Violating {
  @GuardedBy("this")
  private int balance;

  void deposit(int amount) {
    balance += amount;
  }

  synchronized void safe() {
    balance++;
  }
}

class MoneyOk implements Comparable<MoneyOk> {
  double amount;
  int cents;

  public int compareTo(MoneyOk other) {
    return Double.compare(amount, other.amount);
  }

  int compareCents(MoneyOk other) {
    return cents < other.cents ? -1 : cents > other.cents ? 1 : 0;
  }

  int compare(MoneyOk first, MoneyOk second) {
    return first.cents < second.cents ? -1 : first.cents > second.cents ? 1 : 0;
  }

  int order(MoneyOk other) {
    return amount < other.amount ? -1 : amount > other.amount ? 1 : 0;
  }
}

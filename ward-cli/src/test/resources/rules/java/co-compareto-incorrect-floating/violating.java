class Money implements Comparable<Money> {
  double amount;

  public int compareTo(Money other) {
    return amount < other.amount ? -1 : amount > other.amount ? 1 : 0;
  }
}

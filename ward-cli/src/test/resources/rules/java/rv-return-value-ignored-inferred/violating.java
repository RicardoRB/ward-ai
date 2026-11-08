class Money {
  private int cents;

  Money plus(Money other) {
    return new Money();
  }

  Money addTo(Money other) {
    this.cents = other.cents;
    return this;
  }

  void run(Money other) {
    plus(other);
    addTo(other);
    Money kept = plus(other);
  }
}


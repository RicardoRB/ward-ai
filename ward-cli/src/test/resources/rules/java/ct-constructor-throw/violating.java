class Account {
  Account(int balance) {
    if (balance < 0) {
      throw new IllegalArgumentException("negative");
    }
  }
}

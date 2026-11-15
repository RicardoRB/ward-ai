final class Check {
  int depth(int n) {
    if (n == 0) {
      return 0;
    }
    return depth(n - 1) + 1;
  }
  record Address(String city) {}

  record Customer(String name, Address address) {}

  static Customer newCustomer(String n, Address a) {
    return new Customer(n, a);
  }
}

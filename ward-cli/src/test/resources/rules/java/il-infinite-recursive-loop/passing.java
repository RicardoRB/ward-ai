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

  String pad(String text) {
    return pad(text, 2);
  }

  String pad(String text, int width) {
    return pad(text, width, ' ');
  }

  String pad(String text, int width, char fill) {
    return text + width + fill;
  }

  int size(String text) {
    return size(text.toCharArray());
  }

  int size(char[] text) {
    return text.length;
  }
}

final class Check {
  String test(String name) {
    return name.trim();
  }

  String pick(int mode, String name) {
    return switch (mode) {
      case 1 -> name.trim();
      default -> name;
    };
  }
}

@RequiredArgsConstructor
class User {
  private User(String name, int age) {}

  String name() {
    return "user";
  }
}

@UtilityClass
class Helpers {
  private Helpers() {}

  void run() {}
}

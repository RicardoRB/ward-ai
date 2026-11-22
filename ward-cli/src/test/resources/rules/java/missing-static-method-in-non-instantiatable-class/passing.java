class Util {
  private Util() {}

  static Util create() {
    return new Util();
  }
}

class Outer {
  static final class Builder {
    private Builder() {}

    String name() {
      return "builder";
    }
  }

  Builder builder() {
    return new Builder();
  }
}

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

@Module
final class T {
  @Provides
  String name() {
    return "";
  }
}

final class U {
  @Module
  static final class V {
    @Provides
    String name() {
      return "";
    }
  }
}

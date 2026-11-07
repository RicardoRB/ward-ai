@Singleton
final class T {
  @Inject T() {}

  @Provides @Singleton String name() {
    return "";
  }
}

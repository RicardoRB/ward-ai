final class Sample {
  String home() {
    String value = System.getenv("APP_TOKEN");
    return value;
  }
}

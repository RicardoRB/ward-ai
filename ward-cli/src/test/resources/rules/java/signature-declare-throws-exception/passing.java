import java.io.IOException;

final class Runner {
  void run() throws IOException {
    System.out.println("run");
  }

  void wrapped() throws Throwable {}

  @Override
  public void close() throws Exception {}

  @org.junit.jupiter.api.Test
  void test() throws Exception {}

  @org.junit.jupiter.api.BeforeEach
  void setUp() throws Exception {}
}

package app;

import java.util.List;

public class User {
  private final String name;

  public User(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public int port() {
    return 8080;
  }

  public void run() {
    String value = getName();
    int port = port();
    Missing unknown = null;
    List<String> tags = List.of(value);
  }
}

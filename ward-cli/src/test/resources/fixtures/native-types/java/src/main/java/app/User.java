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

  @Override public String toString() { return name; }

  public void read() throws java.io.IOException {}

  public static final String KIND = "user";

  public int port() {
    return 8080;
  }

  public void run() {
    String value = getName();
    int port = port();
    Missing unknown = null;
    List<String> tags = List.of(value);
    boolean same = value.equals("x");
  }
}

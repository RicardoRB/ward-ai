public class Config {
  public String name;
  private String hidden;
  @Inject public String injected;
  public int count;

  Config(String name) {
    this.name = name;
  }

  int size() {
    return name.length() + injected.length() + hidden.length();
  }
}


final class Check {
  private String name;
  private String label;
  private String other;

  Check(String name) {
    this.name = name;
    other = name;
  }

  void init(String value) {
    name = value;
    label = value;
    other = value;
  }

  int length() {
    return name.length() + (label == null ? 0 : label.length());
  }

  int size() {
    return other.length();
  }
}

final class SetByConstructorHelper {
  private String title;

  SetByConstructorHelper() {
    prepare();
  }

  private void prepare() {
    fill();
  }

  private void fill() {
    title = "x";
  }

  int length() {
    return title.length();
  }
}

final class SetBeforeEachTest {
  private String subject;

  @BeforeEach
  void setUp() {
    subject = "x";
  }

  @Test
  void measures() {
    subject.length();
  }
}

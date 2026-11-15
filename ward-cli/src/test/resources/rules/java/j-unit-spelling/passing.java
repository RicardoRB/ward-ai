class ServiceTest {
  void setUp() {}

  @BeforeEach
  void setup() {}

  @AfterEach
  public void teardown() {}

  static final class LabSetup {
    private LabSetup() {}

    LabSetup setup() {
      return this;
    }
  }
}

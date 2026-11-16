import org.junit.jupiter.api.Test;

class ParserTest {
  @Test
  void parses() {}
}

class Facts implements FactTest {
  void helper() {}
}

class SlowParserTest extends ParserTest {
  void helper() {}
}

class TypedParserTest extends base.GenericParserTests<String> implements Runnable {
  public void run() {}
}

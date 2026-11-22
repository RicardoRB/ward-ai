class Registry {
  private static Registry instance = new Registry();

  private Registry() {
  }

  public static Registry getInstance() {
    return instance;
  }
}

class Tokenizer {
  private static final Tokenizer PROTOTYPE = new Tokenizer();

  public Tokenizer() {
  }

  private static Tokenizer clone0() {
    return new Tokenizer();
  }

  public static Tokenizer getCsvInstance() {
    return clone0();
  }
}

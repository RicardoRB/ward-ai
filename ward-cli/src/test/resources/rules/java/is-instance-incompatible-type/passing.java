final class Passing {
  boolean run(Class<CharSequence> type, String text) {


    return type.isInstance(text);
  }
}

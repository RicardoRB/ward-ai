final class Violating {
  boolean run(Message message) {
    return message.getName() == null;
  }
}

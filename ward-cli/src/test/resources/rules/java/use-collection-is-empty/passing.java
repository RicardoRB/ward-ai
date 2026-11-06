final class Queue {
  boolean empty(List<String> items) {
    return items.isEmpty() || items.size() > 1;
  }
}

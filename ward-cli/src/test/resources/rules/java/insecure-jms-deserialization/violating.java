final class Check {
  void run() throws Exception {
    ObjectMessage message = (ObjectMessage) incoming;
    message.getObject();
  }
}

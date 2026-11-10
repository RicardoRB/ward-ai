final class Check {
  void run() throws Exception {
    TextMessage message = (TextMessage) incoming;
    message.getText();
  }
}

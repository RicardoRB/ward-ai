final class Violating {
  Thread run() {
    return new Thread(new Thread(() -> {}));
  }
}

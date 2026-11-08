// passes
final class Check {
  Object test() {
    Object value = new int[3];
    return (int[]) value;
  }
}

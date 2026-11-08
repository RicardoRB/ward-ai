// passes
final class Check {
  Object test() {
    Object value = new Child();
    return (Child) value;
  }
}

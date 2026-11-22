@AllArgsConstructor
final class Order {
  private String label;

  int length() {
    return label.length();
  }
}

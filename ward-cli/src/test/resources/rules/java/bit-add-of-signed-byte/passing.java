// passes
final class Check {
  int test(int high, byte low) {
    return (high << 8) + (low & 0xff);
  }
}

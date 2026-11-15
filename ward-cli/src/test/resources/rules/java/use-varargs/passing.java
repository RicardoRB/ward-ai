import java.io.PrintStream;

final class Joiner {
  String join(String... parts) {
    return String.join(",", parts);
  }

  public static void main(final String[] args) {
    System.out.println(args.length);
  }

  int part(final String[] parts, final int index) {
    return parts[index].length();
  }

  void store(final String key, final byte[] bytes) {
    System.out.println(key + bytes.length);
  }

  int run(final String[] args, final PrintStream out) {
    return args.length;
  }
}

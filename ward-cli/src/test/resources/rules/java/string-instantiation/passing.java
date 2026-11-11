import java.nio.charset.StandardCharsets;

class Strings2 {
  String a() {
    return "bar";
  }

  String b(byte[] bytes, char[] chars) {
    return new String(bytes, StandardCharsets.UTF_8) + new String(chars);
  }
}

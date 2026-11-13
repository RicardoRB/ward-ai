import javax.crypto.spec.IvParameterSpec;

class Ivs {
  IvParameterSpec literal() {
    return new IvParameterSpec("0123456789abcdef".getBytes());
  }

  IvParameterSpec zeros() {
    return new IvParameterSpec(new byte[16]);
  }

  IvParameterSpec listed() {
    return new IvParameterSpec(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
  }
}

import javax.crypto.spec.IvParameterSpec;
class Vectors {
  IvParameterSpec a() {
    return new IvParameterSpec("0123456789abcdef".getBytes());
  }
}

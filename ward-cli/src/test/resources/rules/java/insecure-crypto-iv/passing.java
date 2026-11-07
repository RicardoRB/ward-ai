import javax.crypto.spec.IvParameterSpec;
class Vectors {
  IvParameterSpec a(byte[] iv) {
    return new IvParameterSpec(iv);
  }
}

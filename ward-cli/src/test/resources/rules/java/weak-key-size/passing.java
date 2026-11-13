import java.security.KeyPairGenerator;
import java.security.spec.RSAKeyGenParameterSpec;

class Keys {
  void generate(KeyPairGenerator generator) {
    generator.initialize(3072);
  }

  Object spec() {
    return new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4);
  }
}

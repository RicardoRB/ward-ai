import java.security.KeyPairGenerator;
import java.security.spec.RSAKeyGenParameterSpec;

class Keys {
  void generate(KeyPairGenerator generator) {
    generator.initialize(1024);
  }

  Object spec() {
    return new RSAKeyGenParameterSpec(768, RSAKeyGenParameterSpec.F4);
  }
}

import java.security.SecureRandom;

class Tokens {
  int pin() {
    return new SecureRandom().nextInt(10000);
  }
}

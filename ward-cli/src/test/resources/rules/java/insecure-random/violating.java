import java.util.Random;

class Tokens {
  int pin() {
    return new Random().nextInt(10000);
  }

  double ratio() {
    return Math.random();
  }
}

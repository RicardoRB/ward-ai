package dataflow;

import java.util.function.IntSupplier;

class Lambdas {
  int captured() {
    int c = risky();
    IntSupplier s = () -> c + 1;
    return s.getAsInt();
  }

  int risky() {
    return 1;
  }

  int unusedBeforeLambda() {
    int d = 0;
    d = risky();
    IntSupplier s = () -> risky();
    return s.getAsInt();
  }
}

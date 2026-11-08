final class Sealed {
  Sealed(int balance) {
    if (balance < 0) {
      throw new IllegalArgumentException("negative");
    }
  }
}

class Guarded {
  Guarded(int balance) {
    if (balance < 0) {
      throw new IllegalArgumentException("negative");
    }
  }

  protected final void finalize() {}
}

class Hidden {
  private Hidden(int balance) {
    if (balance < 0) {
      throw new IllegalArgumentException("negative");
    }
  }
}

class Calm {
  Calm(int balance) {
    Runnable r = () -> {
      throw new IllegalStateException();
    };
  }
}

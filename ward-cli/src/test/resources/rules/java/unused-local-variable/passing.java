class Unused {
  int run(int value) {
    int used = value * 2;
    return used;
  }
}

class Patterns {
  sealed interface Shape permits Num, Str {}

  record Num(int value) implements Shape {}

  record Str(String text) implements Shape {}

  int size(Shape shape) {
    return switch (shape) {
      case Num num -> 1;
      case Str str -> 2;
    };
  }

  int record(Shape shape) {
    return switch (shape) {
      case Num(int value) -> 1;
      case Str(String text) -> 2;
    };
  }
}

class Hierarchy {
  Iterable<Class<?>> of(Class<?> type) {
    final Iterable<Class<?>> classes = () -> {
      final java.util.concurrent.atomic.AtomicReference<Class<?>> next =
          new java.util.concurrent.atomic.AtomicReference<>(type);
      return new java.util.Iterator<Class<?>>() {
        @Override
        public boolean hasNext() {
          return next.get() != null;
        }

        @Override
        public Class<?> next() {
          return next.get();
        }
      };
    };
    return classes;
  }
}

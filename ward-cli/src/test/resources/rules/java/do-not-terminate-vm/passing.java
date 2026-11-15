class Shutdown {
  int cores() {
    return Runtime.getRuntime().availableProcessors();
  }
}

class Entry {
  public static void main(final String[] args) {
    System.exit(args.length);
  }
}

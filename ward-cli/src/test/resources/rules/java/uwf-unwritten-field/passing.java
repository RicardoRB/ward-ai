final class Box {
  private String label;

  Box(String label) {
    this.label = label;
  }

  String show() {
    return label;
  }
}

final class Entry {
  private String title;

  private Entry() {}

  static Entry of(String title) {
    Entry entry = new Entry();
    entry.title = title;
    return entry;
  }

  String title() {
    return title;
  }
}

@RequiredArgsConstructor
final class Service {
  private final String label;

  String show() {
    return label;
  }
}

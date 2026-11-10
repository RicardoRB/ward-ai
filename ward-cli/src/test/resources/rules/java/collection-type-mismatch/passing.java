







import java.util.List;

class Sample {
  boolean has(List<String> names) {
    return names.contains("a");
  }

  boolean other(List<String> names, Object any) {
    return names.contains(any);
  }
}

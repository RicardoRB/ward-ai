import java.util.List;
class Arrays2 {
  String[] a(List<String> items) {
    return items.toArray(new String[items.size()]);
  }
}

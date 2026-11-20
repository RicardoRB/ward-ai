import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class Holder {
  List<String> names = new ArrayList<>();

  Object collect(Stream<String> words) {
    return words.collect(
        Collectors.groupingBy(String::length, TreeMap::new, Collectors.toCollection(TreeSet::new)));
  }

  Class<?> type() {
    return java.util.ArrayList.class;
  }
}

final class Names extends ArrayList<String> {
  private static final long serialVersionUID = 1L;
}

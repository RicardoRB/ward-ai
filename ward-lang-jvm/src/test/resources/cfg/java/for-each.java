import java.util.List;

class ForEach {
  void run(List<String> items) {
    for (String item : items) {
      work(item);
    }
    done();
  }
}

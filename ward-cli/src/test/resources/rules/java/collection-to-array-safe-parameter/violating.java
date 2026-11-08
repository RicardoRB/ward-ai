import java.util.List;

class Sample {
  Object f(List<String> names) {
    return names.toArray(new Integer[0]);
  }

  Object g(List<Number> nums) {
    return nums.toArray(new String[0]);
  }
}

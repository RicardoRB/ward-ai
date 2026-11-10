
import java.util.List;

class Sample {
  Object a(List<Number> nums) {
    return nums.toArray(new Integer[0]);
  }

  Object b(List<Object> objs) {
    return objs.toArray(new String[0]);
  }

  Object c(List<Runnable> tasks) {
    return tasks.toArray(new Thread[0]);
  }

  Object d(List<Runnable> tasks) {
    return tasks.toArray(new ArrayList[0]);
  }

  Object e(List<E> items) {
    return items.toArray(new String[0]);
  }
}

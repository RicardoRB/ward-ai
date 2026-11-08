import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class Sample {
  String run(Future<String> f, Duration d) throws Exception {
    return f.get(d.toMillis(), TimeUnit.SECONDS);
  }

  String again(Future<String> f, Duration d) throws Exception {
    return f.get(d.getSeconds(), java.util.concurrent.TimeUnit.MILLISECONDS);
  }
}

import java.time.DayOfWeek;
import java.time.temporal.ChronoField;

final class Passing {
  int run(DayOfWeek day) {
    return day.get(ChronoField.DAY_OF_WEEK);
  }
}

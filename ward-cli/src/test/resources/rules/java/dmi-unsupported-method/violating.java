import java.sql.Date;
import java.sql.Time;

class Sample {
  int hours(Date d, Time t) {
    t.setYear(1);
    return d.getHours() + t.getDay();
  }
}

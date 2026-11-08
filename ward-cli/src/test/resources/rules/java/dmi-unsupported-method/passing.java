
import java.sql.Date;
import java.sql.Time;

class Sample {
  int year(Date d, Time t) {
    return d.getYear() + t.getHours();
  }
}

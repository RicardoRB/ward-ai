package accuracy.sql;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.jdbc.core.JdbcTemplate;

class SqlSpringRequestParam {
  void find(@RequestParam String name, JdbcTemplate jdbc) {
    jdbc.query("select * from users where name = '" + name + "'", null);
  }
}

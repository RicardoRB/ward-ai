package accuracy.sql;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import javax.persistence.EntityManager;

class SqlSpringPathVariable {
  void load(@PathVariable String key, EntityManager entityManager) {
    entityManager.createQuery("select u from User u where u.key = '" + key + "'");
  }
}

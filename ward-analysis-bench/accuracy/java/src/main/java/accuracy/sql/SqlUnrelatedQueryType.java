package accuracy.sql;

import javax.servlet.http.HttpServletRequest;

class SqlUnrelatedQueryType {
  void search(HttpServletRequest request, Query query) {
    String id = request.getParameter("id");
    query.executeQuery(id);
  }
}

class Query {
  void executeQuery(String text) {}
}

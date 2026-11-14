import java.sql.Statement
import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun constant(request: HttpServletRequest, statement: Statement) {
    val unused = request.getParameter("x")
    statement.executeQuery("fixed")
  }

  fun overwritten(request: HttpServletRequest, statement: Statement) {
    var value = request.getParameter("x")
    value = "fixed"
    statement.executeQuery(value)
  }

  fun ktor(statement: Statement) {
    val id = call.parameters["id"]
    statement.executeQuery("select 1")
  }
}

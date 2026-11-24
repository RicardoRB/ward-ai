import java.sql.Statement
import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.*
import org.springframework.jdbc.core.JdbcTemplate

class Subject {
  fun direct(request: HttpServletRequest, statement: Statement) {
    statement.executeQuery(request.getParameter("x"))
  }

  fun propagated(request: HttpServletRequest, statement: Statement) {
    val value = request?.getParameter("x") ?: ""
    val text = "<$value>"
    statement.executeQuery(text)
  }

  fun annotated(@RequestParam value: String, statement: Statement) {
    statement.executeQuery("[${value.trim()}]")
  }

  fun crossMethod(request: HttpServletRequest, statement: Statement) {
    val value = read(request)
    statement.executeQuery(value)
  }

  fun ktor(statement: Statement) {
    val id = call.parameters["id"]
    statement.executeQuery("select * from t where id = $id")
  }

  fun console(statement: Statement) {
    val line = readLine()
    statement.executeQuery("select * from t where name = '" + line + "'")
  }

  fun read(request: HttpServletRequest): String? = request.getParameter("x")
}

class LayeredController(private val users: LayeredUsers) {
  @GetMapping("/users")
  fun listUsers(@RequestParam name: String) {
    users.byName(name)
  }

  fun scopedUsers() {
    call.parameters["n"]?.let { users.byName(it) }
  }

  fun extensionUsers() {
    call.parameters["n"]?.queryUsers(users)
  }
}

class LayeredUsers(private val jdbc: JdbcTemplate) {
  fun byName(wanted: String) {
    jdbc.queryForList("select * from t where n = '$wanted'")
  }
}

fun String.queryUsers(users: LayeredUsers) = users.byName(this)

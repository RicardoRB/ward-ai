import java.sql.Statement
import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.*
import org.springframework.jdbc.core.JdbcTemplate

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

class SafeController(private val accounts: SafeAccounts) {
  @GetMapping("/accounts")
  fun listAccounts(@RequestParam name: String) {
    accounts.byName(name)
  }

  fun extensionAccounts() {
    "fixed".queryAccounts(accounts)
  }
}

class SafeAccounts(private val jdbc: JdbcTemplate) {
  fun byName(wanted: String) {
    jdbc.queryForList("select * from t where n = ?", wanted)
  }
}

fun String.queryAccounts(accounts: SafeAccounts) = accounts.byName(this)

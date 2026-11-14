import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun constant(request: HttpServletRequest) {
    val unused = request.getParameter("x")
    Runtime.getRuntime().exec("fixed")
  }

  fun overwritten(request: HttpServletRequest) {
    var value = request.getParameter("x")
    value = "fixed"
    Runtime.getRuntime().exec(value)
  }

  fun sanitizedInLet(request: HttpServletRequest) {
    val port = request.getParameter("x").let { Integer.parseInt(it) }
    Runtime.getRuntime().exec("run " + port)
  }

  fun branchInsideLet(request: HttpServletRequest) {
    request.getParameter("x")?.let {
      var value = it
      if (value.isEmpty()) {
        value = "fixed"
        Runtime.getRuntime().exec(value)
      }
    }
  }

  fun sanitizedBranch(request: HttpServletRequest) {
    val port = if (request.getParameter("x") == null) "80" else Integer.parseInt(request.getParameter("x")).toString()
    Runtime.getRuntime().exec("run " + port)
  }

  fun sanitizedLetWithDefault(request: HttpServletRequest) {
    val port = request.getParameter("x")?.let { Integer.parseInt(it) } ?: 80
    Runtime.getRuntime().exec("run " + port)
  }

  fun leavesWhenMissing(request: HttpServletRequest) {
    val value = request.getParameter("x") ?: return
    Runtime.getRuntime().exec("fixed")
  }

  val fixedProperty = Runtime.getRuntime().exec("fixed")

  fun readsAProperty(request: HttpServletRequest) {
    val unused = request.queryString
    Runtime.getRuntime().exec("fixed")
  }
}

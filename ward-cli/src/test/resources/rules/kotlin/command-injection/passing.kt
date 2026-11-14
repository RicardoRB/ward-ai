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
}

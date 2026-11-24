import java.io.File
import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun constant(request: HttpServletRequest) {
    val unused = request.getParameter("x")
    File("fixed")
  }

  fun overwritten(request: HttpServletRequest) {
    var value = request.getParameter("x")
    value = "fixed"
    File(value)
  }
}

class SafeFileController(private val files: SafeFileService) {
  fun show(@RequestParam name: String) {
    files.open(name)
  }

  fun shown() {
    "fixed".openFile()
  }
}

class SafeFileService {
  fun open(wanted: String) {
    File("/data/fixed")
  }
}

fun String.openFile() = File(this)

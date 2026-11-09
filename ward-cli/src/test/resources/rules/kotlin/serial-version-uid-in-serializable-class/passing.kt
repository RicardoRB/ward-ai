package fixture

import java.io.Serializable

class Payload(val id: Int) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

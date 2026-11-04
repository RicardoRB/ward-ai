import Foundation

public enum Checkout {
    // a Cart.Line is priced when the order is placed
    case placed(Cart.Line)
}

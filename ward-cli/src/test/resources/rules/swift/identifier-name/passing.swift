let width = 1
struct Sales {
    static func < (lhs: Sales, rhs: Sales) -> Bool { true }
    func total<Amount>(of amount: Amount) -> Amount { amount }
}
enum User {
    case `default`
}

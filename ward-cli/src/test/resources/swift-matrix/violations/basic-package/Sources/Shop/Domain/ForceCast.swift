public enum ForceCast {
    public static func name(_ value: Any) -> String {
        value as! String
    }
}

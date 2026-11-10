fun banner(user: String, count: Int): String {
    val label = "Hello   $user,  you have ${count}  new    messages"
    val raw = """keeps   these   spaces
      and this line"""
    return label + raw
}

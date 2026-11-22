pub fn why(r: Result<i32, String>) -> String {
    r.expect_err("an error")
}

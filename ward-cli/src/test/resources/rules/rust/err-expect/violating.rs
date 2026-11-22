pub fn why(r: Result<i32, String>) -> String {
    r.err().expect("an error")
}

pub fn read(r: Result<i32, String>) -> i32 {
    r.expect("a number")
}

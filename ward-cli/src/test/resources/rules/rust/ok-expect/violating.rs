pub fn read(r: Result<i32, String>) -> i32 {
    r.ok().expect("a number")
}

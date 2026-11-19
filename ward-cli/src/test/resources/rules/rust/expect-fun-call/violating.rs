pub fn read(o: Option<i32>, n: i32) -> i32 {
    o.expect(&format!("missing {}", n))
}

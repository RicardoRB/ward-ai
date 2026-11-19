pub fn read(o: Option<i32>, n: i32) -> i32 {
    o.unwrap_or_else(|| panic!("missing {}", n))
}

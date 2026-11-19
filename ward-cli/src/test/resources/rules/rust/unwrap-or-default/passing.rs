pub fn items(o: Option<Vec<i32>>) -> Vec<i32> {
    o.unwrap_or_default()
}

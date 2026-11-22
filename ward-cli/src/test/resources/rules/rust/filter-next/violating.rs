pub fn first_big(v: &[i32]) -> Option<&i32> {
    v.iter().filter(|x| **x > 1).next()
}

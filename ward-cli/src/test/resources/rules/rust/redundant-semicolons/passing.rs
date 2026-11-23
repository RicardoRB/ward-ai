pub fn run(n: &mut i32) {
    *n += 1;
}

pub fn braced(n: &mut i32) {
    assert_eq! {*n, 1};
    vec! {1};
}

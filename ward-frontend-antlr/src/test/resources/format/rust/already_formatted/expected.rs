use std::fmt;

/// A point.
#[derive(Debug, Clone)]
pub struct Point {
    pub x: i32,
    pub y: i32,
}

impl Point {
    pub fn new(x: i32, y: i32) -> Self {
        Point { x, y }
    }

    pub fn sum(&self) -> i32 {
        self.x + self.y
    }
}

fn main() {
    let p = Point::new(1, 2);
    println!("{:?} {}", p, p.sum());
}

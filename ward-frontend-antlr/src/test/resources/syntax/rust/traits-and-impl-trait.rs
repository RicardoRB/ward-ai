use std::fmt::Debug;

pub trait Shape: Debug {
    const SIDES: u32;
    type Unit: Copy;

    fn area(&self) -> f64;

    fn describe(&self) -> String {
        format!("{:?} with {} sides", self, Self::SIDES)
    }

    fn scale(self, factor: f64) -> Self where Self: Sized;
}

pub unsafe trait Zeroable {}

pub trait Container {
    type Item<'a> where Self: 'a;
    fn first<'a>(&'a self) -> Option<Self::Item<'a>>;
}

pub trait Marker {}

fn print_all(items: &[impl Debug]) {
    for item in items {
        println!("{item:?}");
    }
}

fn make_adder(step: i32) -> impl Fn(i32) -> i32 {
    move |value| value + step
}

fn boxed(flag: bool) -> Box<dyn Iterator<Item = u32>> {
    if flag { Box::new(0..3) } else { Box::new(std::iter::empty()) }
}

fn generic_dyn(shape: &dyn Shape<Unit = u8>) -> f64 {
    shape.area()
}

impl<T: Debug> Shape for Vec<T> {
    const SIDES: u32 = 0;
    type Unit = u8;

    fn area(&self) -> f64 {
        0.0
    }

    fn scale(self, _factor: f64) -> Self {
        self
    }
}

pub trait Stream {
    async fn next(&mut self) -> Option<u8>;
    fn pending(&self) -> impl Iterator<Item = u8> + '_;
}

impl<T> From<T> for Wrapper<T> {
    fn from(inner: T) -> Self {
        Wrapper(inner)
    }
}

pub struct Wrapper<T>(pub T);

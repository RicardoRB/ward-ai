mod shapes {
    pub struct Circle { pub r: u32 }
    pub fn area(c: &Circle) -> u32 { c.r }
}
pub use shapes::Circle;
pub use self::shapes::area as surface;

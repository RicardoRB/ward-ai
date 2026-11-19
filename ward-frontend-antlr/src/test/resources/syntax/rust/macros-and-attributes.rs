#![allow(dead_code)]
#![cfg_attr(not(test), no_std)]

macro_rules! square {
    ($value:expr) => {
        $value * $value
    };
}

macro_rules! make_struct {
    ($name:ident { $($field:ident : $kind:ty),* $(,)? }) => {
        #[derive(Debug, Clone)]
        struct $name {
            $($field: $kind),*
        }
    };
}

macro_rules! count {
    () => (0usize);
    ($head:tt $($tail:tt)*) => (1usize + count!($($tail)*));
}

make_struct!(Point { x: i32, y: i32 });

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
#[repr(C)]
pub struct Header {
    #[allow(unused)]
    pub tag: u8,
    #[cfg(feature = "extra")]
    pub extra: u32,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn squares() {
        assert_eq!(square!(3), 9);
        assert!(matches!(count!(a b c), 3));
        let items = vec![1, 2, 3];
        let text = format!("{items:?} {}", items.len());
        println!("{text}");
        let _ = dbg!(items);
    }
}

#[inline(always)]
#[must_use = "the value is needed"]
pub fn id<T>(value: T) -> T {
    value
}

#[proc_macro_attribute]
pub fn tagged(_attr: u8, item: u8) -> u8 {
    item
}

const _: () = assert!(std::mem::size_of::<u32>() == 4);

#[unsafe(no_mangle)]
pub extern "C" fn unsafe_attribute() {}

fn captures<'a, T>(value: &'a T) -> impl Sized + use<'a, T> {
    value
}

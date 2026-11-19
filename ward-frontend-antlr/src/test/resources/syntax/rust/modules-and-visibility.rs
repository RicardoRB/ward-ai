//! Crate level documentation.

pub mod geometry {
    pub(crate) mod shapes {
        pub(super) fn helper() {}
        pub(in crate::geometry) fn scoped() {}
    }

    pub use self::shapes::helper as assist;
}

mod empty;

use crate::geometry::{self, shapes::*};
use std::{io::{self, Read, Write}, sync::Arc};
pub use std::collections::*;
use ::std::mem as memory;

extern crate alloc as heap;

extern "C" {
    pub fn abs(value: i32) -> i32;
}

unsafe extern "C" {
    pub safe fn strlen_safe(text: *const u8) -> usize;
    pub unsafe fn raw_free(ptr: *mut u8);
}

#[no_mangle]
pub extern "C" fn exported(value: i32) -> i32 {
    value
}

pub static GREETING: &str = "hello";
static mut COUNTER: u32 = 0;
const LIMITS: [u32; 3] = [1, 2, 3];

/// Documented item.
pub enum Level {
    Low = 1,
    High = 10,
}

pub union Bits {
    int: u32,
    float: f32,
}

pub struct Unit;
pub struct Tuple(pub u8, pub(crate) u16);

pub const fn constant(value: u32) -> u32 {
    value * 2
}

pub unsafe fn danger() {}

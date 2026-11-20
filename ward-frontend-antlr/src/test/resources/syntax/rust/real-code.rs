#![allow(
    clippy::needless_return,
    clippy::too_many_lines
)]
#![cfg(any(
    feature = "full",
    all(target_os = "linux", feature = "rt")
))]
/*!
 * Inner block documentation may hold *stars*, a slash / and
 * /* a nested comment with * inside */ before it ends.
 */

/* a plain block comment with ** and / inside, /* nested */ again */

/// An empty documentation line follows.
///
macro_rules! holder {
    (
        #![$meta:meta]
        $($item:item)*
    ) => {
        $(
            #[cfg($meta)]
            $item
        )*
    };
    ($type:ty: $($tok:tt)*) => {
        impl Marker for $type {}
    };
}

//
fn after_empty_comment() -> u8 {
    1
}

pub trait Marker {}

fn strings() -> bool {
    let a = b"\\";
    let b = "\\";
    let c = r"\\";
    let d = b"a\"b";
    let e = "line one \
             continues";
    let f = '\'';
    let g = '\\';
    a.len() + b.len() + c.len() + d.len() + e.len() > usize::from(f == g)
}

fn closure_over_references(values: &[&u8]) -> usize {
    let above = |value: &&u8| **value > 3;
    values.iter().filter(|value| above(value)).count()
}

fn discards(input: u32) {
    _ = input.checked_add(1);
    let _ = input;
}

fn tabbed() {
	let indented = 1;
	_ = indented;
}

fn guarded(flag: bool) -> u32 {
    #[cfg(debug_assertions)]
    {
        unsafe fn check(value: *const u32) -> u32 {
            unsafe { *value }
        }
        let n = 1;
        return unsafe { check(&n) };
    }
    #[allow(unreachable_code)]
    if flag {
        1
    } else {
        2
    }
}

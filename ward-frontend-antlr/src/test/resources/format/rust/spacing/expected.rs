fn main() {
    let a = 1 + 2 * 3;
    let b = -a;
    let c = !true && false;
    let d = &a;
    let e = *&a;
    let f = a as f64 / 2.0;
    let g = (a, b);
    let h = [1, 2, 3];
    let i = h[0];
    let j = a < b;
    let k = a << 2;
    let l = a >> 1;
    let m = 0..10;
    let n = 0..=10;
    let o = a == b;
    let mut p = 5;
    p += 1;
    p -= 1;
    let q: [u8; 4] = [0; 4];
    let r: &str = "x";
    let s = Some(1);
    let t = foo(1, 2);
    let u = std::cmp::max(1, 2);
    let v = a.b.c;
    let w = x?;
    let y = if a { 1 } else { 2 };
}

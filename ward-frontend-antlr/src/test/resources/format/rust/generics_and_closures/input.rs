use std::collections::HashMap;

struct Cache<K,V>
where
    K:Eq,
{
map:HashMap<K,V>,
}

fn first<'a,T:Display+Clone>(items:&'a [T],f:impl Fn(&T)->bool)->Option<&'a T>{
    items.iter().find(|x|f(x))
}

fn main() {
    let m:HashMap<String,Vec<u8>> = HashMap::new();
    let v = Vec::<u8>::with_capacity(4);
    let n = "5".parse::<i32>().unwrap();
    let f = |a:i32,b:i32|->i32{a+b};
    let g = move||1;
    let h = |&x| x*2;
    let k = pair(|_|1,|_|2);
    let c = <i32 as Default>::default();
    let b:Box<dyn Fn(i32)->i32+Send> = Box::new(|x| x);
}

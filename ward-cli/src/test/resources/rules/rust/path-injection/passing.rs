use std::fs;
use std::path::Path;

fn show() {
    let name = std::env::args().nth(1).unwrap();
    let last = Path::new(&name).file_name().unwrap();
    let text = fs::read_to_string(Path::new("/srv/files").join(last)).unwrap();
    println!("{text}");
}

fn fixed() {
    fs::read_to_string("/etc/hostname").unwrap();
}

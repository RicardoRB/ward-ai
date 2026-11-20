use std::process::Command;

fn main() {
    let count = std::env::args().nth(1).unwrap().parse::<u32>().unwrap();
    Command::new("head").arg("-n").arg(count.to_string()).status().unwrap();
    Command::new("ls").arg("-l").status().unwrap();
}

fn fixed() {
    let name = String::from("report");
    Command::new("cat").arg(name).status().unwrap();
}

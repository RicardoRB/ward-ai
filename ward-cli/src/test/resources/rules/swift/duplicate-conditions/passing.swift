if x < 5, y == "s" {
    foo()
} else if x < 5 {
    bar()
}
switch x {
case "a" where y == "s":
    foo()
case "a" where y == "t":
    bar()
}

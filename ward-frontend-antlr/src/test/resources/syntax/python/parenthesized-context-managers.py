with (open("a.txt") as first, open("b.txt") as second):
    print(first, second)

with (
    open("c.txt") as third,
    open("d.txt") as fourth,
):
    print(third, fourth)

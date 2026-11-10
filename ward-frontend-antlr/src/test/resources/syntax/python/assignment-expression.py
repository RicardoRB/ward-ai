import re
items = [1, 2, 3]
if (count := len(items)) > 2:
    print(count)
while (chunk := items.pop()) is not None:
    print(chunk)
squares = [y for x in items if (y := x * x) > 1]
print(f(n := 10), items[index := 0], {value := 1})

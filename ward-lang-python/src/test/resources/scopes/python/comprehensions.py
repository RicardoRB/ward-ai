items = [1, 2]
squares = [n * n for n in items if n]
pairs = {k: v for k, v in zip(items, items)}
gen = (m for m in items for j in range(m))
leak = n
nested = [[y for y in row] for row in items]
fn = lambda q, r=items: q + r

value = 3.14159
width = 10
label = f"{value:>{width}.2f} {value!r} {'quoted'!s:^10}"
nested = f"{'a' if width else 'b'}: {value=}"

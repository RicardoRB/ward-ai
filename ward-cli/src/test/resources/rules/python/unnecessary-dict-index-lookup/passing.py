for key, value in data.items():
    print(value)
for key, value in data.items():
    if value is None:
        data[key] = 0

try:
    value = 1
except (OSError, ValueError) as error:
    raise

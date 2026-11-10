#!/usr/bin/env python
"""Command-line entry point for Django."""
import os
import sys

from django.core.management import execute_from_command_line


def main() -> None:
    """Runs Django's management commands."""
    os.environ.setdefault("DJANGO_SETTINGS_MODULE", "mysite.settings")
    execute_from_command_line(sys.argv)


if __name__ == "__main__":
    main()

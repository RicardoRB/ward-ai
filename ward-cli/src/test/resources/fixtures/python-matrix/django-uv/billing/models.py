"""Fixture code for the Python matrix."""

from django.db import models


class Invoice(models.Model):
    """An invoice with a total."""
    total = models.IntegerField()

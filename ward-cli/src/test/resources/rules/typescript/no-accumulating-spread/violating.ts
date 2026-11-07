const byId = rows.reduce((acc, row) => ({ ...acc, [row.id]: row }), {});

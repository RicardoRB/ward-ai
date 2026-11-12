# Bundled OpenAPI schemas

`oas-3.0.json` is the JSON Schema of OpenAPI 3.0 documents, `$id`
`https://spec.openapis.org/oas/3.0/schema/2019-04-02`, published by the OpenAPI Initiative in
`OAI/OpenAPI-Specification` (`schemas/v3.0/schema.json`) under the Apache License 2.0.

The copy is the file of `@stoplight/spectral-rulesets` 1.23.1 (`dist/oas/schemas/oas/v3.0.json`,
sha-256 `b9cadfc224c409c14e9dea83424e033643e323cd288aaae413b715f806245e3c`), re-indented. Ward reads it with its
own JSON Schema validator to report what the `oas3-schema` rule of Spectral reports.

## OpenAPI 3.1 and JSON Schema 2020-12

- `oas-3.1.json`, `oas-3.1-dialect.json`, `oas-3.1-meta.json`: the OpenAPI 3.1 schema
  (`https://spec.openapis.org/oas/3.1/schema/2021-09-28`) and its Schema Object dialect and
  vocabulary, from the OpenAPI Initiative (Apache License 2.0), as shipped in
  `@stoplight/spectral-rulesets` 1.23.1 (`dist/oas/schemas/oas/v3.1`).
- `stoplight-2020-12.json`, `stoplight-2020-12-validation.json`: Stoplight's variant of the JSON
  Schema 2020-12 meta-schema, with custom error messages, from the same package
  (`dist/oas/schemas/json-schema/draft-2020-12`, Apache License 2.0).
- `json-schema-2020-12*.json`: the JSON Schema 2020-12 meta-schema and the vocabularies it names,
  as shipped in `ajv` 8.20.0 (`dist/refs/json-schema-2020-12`, MIT License; the schemas are the
  JSON Schema organization's).
- `json-schema-2020-12-validation.json` is the vocabulary of the same set. `json-schema-draft-04.json` is the JSON Schema draft 4 meta-schema as shipped in `ajv-draft-04` 1.0.0 (`dist/refs`, MIT License). Both check that the schema of an example is itself a valid schema.

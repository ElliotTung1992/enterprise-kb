# Progress: Markdown Image RAG

## 2026-05-27

- Created planning files for Markdown image RAG implementation.
- Initial design source: `docs/design-md-image-rag.md`.
- Completed initial inspection of md ingestion, vector/keyword search, citation assembly, object storage, and Liquibase layout.
- Added md image asset schema/model/mapper, md child image fields, parser integration, synchronous worker persistence, DashScope/Noop understanding services, and citation/search field extensions.
- Added targeted parser and citation tests for image child behavior and citation asset fields.
- First test run failed because temp `.img` files on macOS were detected as `application/x-apple-diskimage`; adjusted MIME detection to prefer objectKey extension.
- Second test run exposed tail text merging into image slices; adjusted packing so image slices stay standalone.
- Final targeted test run passed: `mvn test -pl kb-document,kb-search -am -DskipTests=false` with 120 tests, 0 failures, 0 errors, 2 skipped.

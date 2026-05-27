# Findings: Markdown Image RAG

## Current Code Shape

- `MdDocumentServiceImpl` uploads `.md` files to MinIO and starts `MdDocumentIngestionWorker`.
- `MdDocumentIngestionWorkerImpl` downloads the md file, calls `MarkdownStructureIngestionService.parse`, inserts parent/child chunks, and writes vectors.
- `MarkdownStructureIngestionServiceImpl` currently splits headings, paragraphs, tables, lists, and fenced code blocks. It does not parse image syntax.
- `MdChildChunk` currently has no `contentType` or `assetId`.
- `MdVectorSearchServiceImpl` maps vector metadata to `SearchHit`, currently hardcoding `contentType = MD_CHILD`.
- `SearchHit` and `Citation` currently have `assetId`, but not `assetUrl` or `assetTitle`.
- Project docs are ignored by `.gitignore`, so `docs/design-md-image-rag.md` exists locally but is not tracked.

## Implementation Notes

- `md_document_assets.child_chunk_id` and `md_child_chunk.asset_id` create a bidirectional relation. Use preallocated IDs and insert assets first with `child_chunk_id = null`, then insert children, then update asset child links.
- Parent content must include image summaries so existing parent expansion can feed image semantics to `MdQnAServiceImpl`.
- Image URL parsing should produce `imageUrl` and `objectKey` from the configured fixed MinIO endpoint and bucket.
- `kb-document` should not depend on `kb-search`, so the md image understanding provider belongs in `kb-document`.
- `DocumentObjectStorageService` only exposes download-to-file; image ingestion can use that path to inspect MIME, size, and bytes.
- `db.changelog-master.xml` currently includes changesets through 029; md image assets should use 030.

# Task Plan: Markdown Image RAG

Goal: implement Markdown image RAG for the `md-documents` vertical slice, based on `docs/design-md-image-rag.md`.

## Success Criteria

- Markdown image syntax with a fixed MinIO URL is parsed during md ingestion.
- Images are synchronously validated, downloaded, understood, and indexed.
- `md_document_assets` stores md-only image metadata.
- Each image reference creates an `IMAGE_CAPTION` child chunk linked to an asset.
- Enhanced parent content includes image summaries for parent expansion.
- md search / QnA citations can return image URL and title.
- Tests cover parsing, failure boundaries, duplicate image reuse, and citation fields where practical.

## Phases

| Phase | Status | Verify |
|---|---|---|
| 1. Inspect current md ingestion/search contracts | complete | Findings captured in `findings.md` |
| 2. Add DB/model/mapper support for md image assets | complete | Compilation of document module |
| 3. Add md image URL parsing and understanding abstraction | complete | Unit tests for URL/understanding boundaries |
| 4. Integrate image blocks into Markdown structure ingestion | complete | Unit tests for image child ordering, code block exclusion, duplicate reuse |
| 5. Wire worker cleanup/persistence/vector metadata | complete | Mapper and ingestion tests pass |
| 6. Extend search/QnA citation data | complete | Search/citation tests pass |
| 7. Run targeted Maven tests | complete | Relevant module tests pass or failures documented |

## Decisions

- Only `md-documents` vertical slice is in scope.
- Images use complete MinIO URLs with one fixed endpoint and bucket.
- Image handling is synchronous; any image failure fails the whole md import.
- Add `md_document_assets`; do not reuse `document_assets`.
- Each image reference creates one asset record and one `IMAGE_CAPTION` child.
- Same objectKey in one document reuses the visual understanding result, but creates separate asset and child records.
- Only standard Markdown image syntax is supported; HTML `<img>` and fenced code block images are ignored.
- Supported image MIME types: PNG, JPEG, WebP. Defaults: max 50 images, max 10 MB each.

## Errors Encountered

| Error | Attempt | Resolution |
|---|---|---|
| macOS detects temp `.img` test files as `application/x-apple-diskimage` | First Maven test run | Prefer objectKey extension for image MIME detection before `Files.probeContentType` |
| Tail orphan text merged back into previous image slice and erased asset association | Second Maven test run | Prevent tail orphan merge when previous slice is an image asset slice |

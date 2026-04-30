--liquibase formatted sql

--changeset ielts:002-create-examples
CREATE TABLE IF NOT EXISTS ielts_examples (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type VARCHAR(30) NOT NULL,
    content_id   UUID NOT NULL,
    sentence     TEXT,
    translation  TEXT,
    note         TEXT,
    sort_order   INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_examples_content ON ielts_examples (content_type, content_id);

--changeset ielts:002-migrate-word-examples
INSERT INTO ielts_examples (id, content_type, content_id, sentence, translation, sort_order, created_at)
SELECT gen_random_uuid(), 'WORD', id, example_sentence, example_translation, 0, now()
FROM ielts_words
WHERE example_sentence IS NOT NULL;

--changeset ielts:002-migrate-phrase-examples
INSERT INTO ielts_examples (id, content_type, content_id, sentence, translation, sort_order, created_at)
SELECT gen_random_uuid(), 'PHRASE', id, example_sentence, example_translation, 0, now()
FROM ielts_phrases
WHERE example_sentence IS NOT NULL;

--changeset ielts:002-migrate-paraphrase-examples
INSERT INTO ielts_examples (id, content_type, content_id, sentence, translation, sort_order, created_at)
SELECT gen_random_uuid(), 'PARAPHRASE', id, example_original, example_paraphrased, 0, now()
FROM ielts_paraphrase_groups
WHERE example_original IS NOT NULL;

--changeset ielts:002-migrate-pronunciation-examples
INSERT INTO ielts_examples (id, content_type, content_id, sentence, translation, sort_order, created_at)
SELECT gen_random_uuid(), 'PRONUNCIATION', id, examples, NULL, 0, now()
FROM ielts_pronunciation_points
WHERE examples IS NOT NULL;

--changeset ielts:002-migrate-grammar-examples
INSERT INTO ielts_examples (id, content_type, content_id, sentence, translation, sort_order, created_at)
SELECT gen_random_uuid(), 'GRAMMAR_POINT', id, examples, NULL, 0, now()
FROM ielts_grammar_points
WHERE examples IS NOT NULL;

--changeset ielts:002-drop-word-example-columns
ALTER TABLE ielts_words DROP COLUMN IF EXISTS example_sentence;
ALTER TABLE ielts_words DROP COLUMN IF EXISTS example_translation;

--changeset ielts:002-drop-phrase-example-columns
ALTER TABLE ielts_phrases DROP COLUMN IF EXISTS example_sentence;
ALTER TABLE ielts_phrases DROP COLUMN IF EXISTS example_translation;

--changeset ielts:002-drop-paraphrase-example-columns
ALTER TABLE ielts_paraphrase_groups DROP COLUMN IF EXISTS example_original;
ALTER TABLE ielts_paraphrase_groups DROP COLUMN IF EXISTS example_paraphrased;

--changeset ielts:002-drop-pronunciation-example-columns
ALTER TABLE ielts_pronunciation_points DROP COLUMN IF EXISTS examples;

--changeset ielts:002-drop-grammar-example-columns
ALTER TABLE ielts_grammar_points DROP COLUMN IF EXISTS examples;

--liquibase formatted sql

--changeset ielts:001-words
CREATE TABLE IF NOT EXISTS ielts_words (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    word            VARCHAR(100) NOT NULL UNIQUE,
    phonetic_uk     VARCHAR(100),
    phonetic_us     VARCHAR(100),
    part_of_speech  VARCHAR(20),
    definition_zh   TEXT,
    definition_en   TEXT,
    example_sentence        TEXT,
    example_translation     TEXT,
    frequency_level SMALLINT DEFAULT 3 CHECK (frequency_level BETWEEN 1 AND 5),
    word_list       VARCHAR(50),
    difficulty      SMALLINT DEFAULT 2 CHECK (difficulty BETWEEN 1 AND 3),
    skill_tags      VARCHAR(100),
    topic_tags      VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

--changeset ielts:001-phrases
CREATE TABLE IF NOT EXISTS ielts_phrases (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phrase              VARCHAR(300) NOT NULL UNIQUE,
    meaning_zh          TEXT,
    usage_note          TEXT,
    example_sentence    TEXT,
    example_translation TEXT,
    category            VARCHAR(50),
    difficulty          SMALLINT DEFAULT 2 CHECK (difficulty BETWEEN 1 AND 3),
    skill_tags          VARCHAR(100),
    topic_tags          VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

--changeset ielts:001-paraphrase-groups
CREATE TABLE IF NOT EXISTS ielts_paraphrase_groups (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_name          VARCHAR(200) NOT NULL,
    core_expression     VARCHAR(200) NOT NULL,
    synonyms            TEXT,
    usage_note          TEXT,
    example_original    TEXT,
    example_paraphrased TEXT,
    difficulty          SMALLINT DEFAULT 2 CHECK (difficulty BETWEEN 1 AND 3),
    skill_tags          VARCHAR(100),
    topic_tags          VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

--changeset ielts:001-pronunciation-points
CREATE TABLE IF NOT EXISTS ielts_pronunciation_points (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(200) NOT NULL,
    category        VARCHAR(50) CHECK (category IN ('stress','linking','weak-form','intonation','elision','assimilation')),
    explanation_zh  TEXT,
    rule_summary    TEXT,
    examples        TEXT,
    common_mistakes TEXT,
    difficulty      SMALLINT DEFAULT 2 CHECK (difficulty BETWEEN 1 AND 3),
    skill_tags      VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

--changeset ielts:001-grammar-points
CREATE TABLE IF NOT EXISTS ielts_grammar_points (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(200) NOT NULL,
    category        VARCHAR(50),
    explanation_zh  TEXT,
    explanation_en  TEXT,
    key_rules       TEXT,
    examples        TEXT,
    common_errors   TEXT,
    difficulty      SMALLINT DEFAULT 2 CHECK (difficulty BETWEEN 1 AND 3),
    skill_tags      VARCHAR(100),
    topic_tags      VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

--changeset ielts:001-grammar-exercises
CREATE TABLE IF NOT EXISTS ielts_grammar_exercises (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grammar_point_id UUID REFERENCES ielts_grammar_points(id) ON DELETE SET NULL,
    question_type    VARCHAR(30) NOT NULL CHECK (question_type IN ('fill-in-blank','error-correction','sentence-transformation','multiple-choice')),
    question         TEXT NOT NULL,
    options          TEXT,
    answer           TEXT NOT NULL,
    explanation      TEXT,
    difficulty       SMALLINT DEFAULT 2 CHECK (difficulty BETWEEN 1 AND 3),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_grammar_exercises_point ON ielts_grammar_exercises (grammar_point_id);

--changeset ielts:001-speaking-topics
CREATE TABLE IF NOT EXISTS ielts_speaking_topics (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title            VARCHAR(200) NOT NULL,
    part             SMALLINT NOT NULL CHECK (part IN (1, 2, 3)),
    question         TEXT NOT NULL,
    reference_answer TEXT,
    key_vocabulary   TEXT,
    useful_phrases   TEXT,
    difficulty       SMALLINT DEFAULT 2 CHECK (difficulty BETWEEN 1 AND 3),
    topic_tags       VARCHAR(255),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

--changeset ielts:001-listening-items
CREATE TABLE IF NOT EXISTS ielts_listening_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title               VARCHAR(200) NOT NULL,
    section             SMALLINT NOT NULL CHECK (section BETWEEN 1 AND 4),
    question_type       VARCHAR(50),
    context_description TEXT,
    script_excerpt      TEXT,
    questions           TEXT,
    answers             TEXT,
    tips                TEXT,
    difficulty          SMALLINT DEFAULT 2 CHECK (difficulty BETWEEN 1 AND 3),
    topic_tags          VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

--changeset ielts:001-reading-items
CREATE TABLE IF NOT EXISTS ielts_reading_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(300) NOT NULL,
    training_type   VARCHAR(20) NOT NULL DEFAULT 'ACADEMIC' CHECK (training_type IN ('ACADEMIC','GENERAL')),
    difficulty      SMALLINT DEFAULT 2 CHECK (difficulty BETWEEN 1 AND 3),
    passage_text    TEXT,
    question_type   VARCHAR(50),
    questions       TEXT,
    answers         TEXT,
    key_vocabulary  TEXT,
    tips            TEXT,
    topic_tags      VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

--changeset ielts:001-writing-tasks
CREATE TABLE IF NOT EXISTS ielts_writing_tasks (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title             VARCHAR(200) NOT NULL,
    task_number       SMALLINT NOT NULL CHECK (task_number IN (1, 2)),
    training_type     VARCHAR(20) NOT NULL DEFAULT 'ACADEMIC' CHECK (training_type IN ('ACADEMIC','GENERAL')),
    task_type         VARCHAR(50),
    prompt            TEXT NOT NULL,
    image_description TEXT,
    model_answer      TEXT,
    band_score_note   TEXT,
    key_phrases       TEXT,
    difficulty        SMALLINT DEFAULT 2 CHECK (difficulty BETWEEN 1 AND 3),
    topic_tags        VARCHAR(255),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

--changeset ielts:001-study-records
CREATE TABLE IF NOT EXISTS ielts_study_records (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type     VARCHAR(30) NOT NULL CHECK (content_type IN ('WORD','PHRASE','PARAPHRASE','PRONUNCIATION','GRAMMAR_POINT','GRAMMAR_EXERCISE','SPEAKING','LISTENING','READING','WRITING')),
    content_id       UUID NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'LEARNING' CHECK (status IN ('LEARNING','REVIEWING','MASTERED')),
    ease_factor      DECIMAL(4,2) NOT NULL DEFAULT 2.50,
    interval_days    INT NOT NULL DEFAULT 1,
    repetition_count INT NOT NULL DEFAULT 0,
    next_review_at   DATE,
    last_reviewed_at TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (content_type, content_id)
);
CREATE INDEX IF NOT EXISTS idx_study_records_review ON ielts_study_records (next_review_at, status);
CREATE INDEX IF NOT EXISTS idx_study_records_type ON ielts_study_records (content_type, status);

--changeset ielts:001-review-logs
CREATE TABLE IF NOT EXISTS ielts_review_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    record_id   UUID NOT NULL REFERENCES ielts_study_records(id) ON DELETE CASCADE,
    rating      VARCHAR(10) NOT NULL CHECK (rating IN ('AGAIN','HARD','GOOD','EASY')),
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_review_logs_date ON ielts_review_logs (reviewed_at);

--changeset ielts:001-daily-plans
CREATE TABLE IF NOT EXISTS ielts_daily_plans (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_date       DATE NOT NULL UNIQUE,
    total_items     INT NOT NULL DEFAULT 0,
    completed_items INT NOT NULL DEFAULT 0,
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

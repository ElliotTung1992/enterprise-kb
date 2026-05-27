-- ⚠ 不是 Liquibase migration —— 不在 db.changelog-master.xml 中，不会自动执行。
-- 用途：md 语料已 ingest 入 md_child_chunk 后，手动构建 jieba 自训练 tokenizer + 自动填充 trigger，并回填存量。
-- 前置：029 已 apply（pg_tokenizer / vchord_bm25 扩展、bm25vector 列、bm25 索引就位），
--       且 md_child_chunk 已有代表性语料（custom model 的词表从 embed_text 列学习，空表学不出有效词表）。
--
-- ✅ 已对运行镜像核实（vchord_bm25 0.3.0 / pg_tokenizer 0.1.1，2026-05-27）：
--   (a) 函数在 tokenizer_catalog / bm25_catalog schema，但二者已在 search_path 上 → 可不加前缀直接调；
--   (b) create_custom_model_tokenizer_and_trigger 存在且签名含 target_column text；tokenize 返回 integer[]，
--       且存在 integer[] → bm25vector 的【隐式】cast，故 trigger 写入 bm25vector 列、回填 UPDATE 均成立；
--   (c) jieba TOML 形状 [pre_tokenizer.jieba] 已据官方 03-examples.md 确认。
--   ⚠ 仍未实测：jieba custom model 须在【非空语料】上才能学出有效词表（本脚本须 ingest 后再跑）。

-- 1. text analyzer：jieba 中文分词
SELECT create_text_analyzer('md_ta', $$
[pre_tokenizer.jieba]
$$);

-- 2. 自训练 model + tokenizer + 自动填充 trigger（词表从 md_child_chunk.embed_text 学习）
--    trigger 在 INSERT / UPDATE embed_text 时自动把切词结果写入 bm25vector，新行无需应用层介入（方式 1）。
SELECT create_custom_model_tokenizer_and_trigger(
    tokenizer_name     => 'md_tok',
    model_name         => 'md_model',
    text_analyzer_name => 'md_ta',
    table_name         => 'md_child_chunk',
    source_column      => 'embed_text',
    target_column      => 'bm25vector'
);

-- 3. 回填存量行（trigger 只对其创建后的写入生效；已有行需一次性回填）
UPDATE md_child_chunk SET bm25vector = tokenize(embed_text, 'md_tok')::bm25vector
WHERE bm25vector IS NULL;

-- 4. 验证（设计 §11 step 6 前的 sanity check）
-- SELECT count(*) FILTER (WHERE bm25vector IS NULL) AS still_null, count(*) AS total FROM md_child_chunk;
-- SELECT tokenize('测试中文分词效果', 'md_tok')::bm25vector;   -- 应返回非空 bm25vector

-- ──────────────────────────────────────────────────────────────────────────
-- 回退方案：若 create_custom_model_tokenizer_and_trigger 不支持 bm25vector 目标列，改为手动三步：
--
-- SELECT create_custom_model('md_model', $$
--   table = 'md_child_chunk'
--   column = 'embed_text'
--   text_analyzer = 'md_ta'
-- $$);
-- SELECT create_tokenizer('md_tok', $$
--   text_analyzer = 'md_ta'
--   model = 'md_model'
-- $$);
-- CREATE FUNCTION md_bm25_fill() RETURNS trigger AS $func$
--   BEGIN NEW.bm25vector := tokenize(NEW.embed_text, 'md_tok')::bm25vector; RETURN NEW; END;
-- $func$ LANGUAGE plpgsql;
-- CREATE TRIGGER trg_md_bm25 BEFORE INSERT OR UPDATE OF embed_text ON md_child_chunk
--   FOR EACH ROW EXECUTE FUNCTION md_bm25_fill();
-- 然后跑上面第 3 步的回填 UPDATE。
--
-- ⚠ 后续加术语 / 同义词（create_synonym 等）会改 model 词表 → 必须 flag=TRGM 保护下
--   重跑回填 UPDATE + REINDEX INDEX idx_md_child_bm25;（设计 §13 接受此代价，仅 md 竖井可控）。

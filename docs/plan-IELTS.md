# kb-ielts 模块开发计划

## 一、模块定位

新增 `kb-ielts` 模块，作为**完全独立的 Spring Boot 应用**，仅依赖 `kb-common`（公共工具类），**独立启动，不挂载到 kb-app**。

- **独立进程**：有自己的 main class、`application.yml`、端口（**8082**）、Liquibase 迁移
- **无认证、无权限校验**，所有接口直接开放
- **单学习者模式**：不引入用户体系，所有学习记录为全局唯一，无 `user_id` 概念
- 与现有知识库（kb-user、kb-document、kb-search 等）完全解耦
- 仍属于同一个 Maven 多模块项目（共享父 pom 的依赖管理），但 kb-app 不依赖它

**依赖关系**：`kb-common → kb-ielts`（自身可执行 JAR）

---

## 二、核心功能

### 2.1 数据内容管理（按四技能设计）

内容按雅思四项技能分类，单词和短语作为跨技能的基础资源，通过 `skill_tags` 标注适用范围。

**统一难度字段**：所有十类内容表均包含 `difficulty` 字段，取值 1-3，用于每日学习计划的分层和筛选：

| 难度值 | 标签 | 说明 |
|--------|------|------|
| 1 | 基础 | 高频核心内容，入门必学 |
| 2 | 中级 | 常见进阶内容，备考重点（默认值） |
| 3 | 高级 | 低频难点，冲高分必备 |

| 内容类型 | 所属技能 | 说明 |
|----------|----------|------|
| 核心单词 `ielts_words` | 跨技能 | 词形、音标、词性、中英释义、例句、频率级别、词表来源、适用技能标签 |
| 常用短语 `ielts_phrases` | 跨技能 | 短语原文、中文含义、适用技能（听力信号词 / 口语连接词 / 写作句型等） |
| 同义替换组 `ielts_paraphrase_groups` | 跨技能 | 核心表达 + 全部同义替换词/短语 + 用法区别 + 改写前后例句对比 |
| 发音要点 `ielts_pronunciation_points` | 听力 / 口语 | 连读 / 弱读 / 重音 / 语调 / 省音规则讲解、例词例句、常见错误 |
| 语法要点 `ielts_grammar_points` | 写作 / 口语 | 语法分类、中英文讲解、规则总结、例句、常见错误 |
| 语法练习 `ielts_grammar_exercises` | 写作 / 口语 | 填空 / 改错 / 句子转换 / 选择题，关联具体语法要点 |
| 口语话题 `ielts_speaking_topics` | Speaking | Part 1/2/3 题目，参考思路，高分词汇 |
| 听力练习 `ielts_listening_items` | Listening | Section 1-4，题型（填空/选择/配对/地图），语境描述，题目与答案，作答技巧 |
| 阅读练习 `ielts_reading_items` | Reading | 文章段落（学术/普通），题型（判断题/配对/句子填空等），题目与答案解析 |
| 写作题目 `ielts_writing_tasks` | Writing | Task 1（图表/信件）/ Task 2（议论文），范文，评分要点，常用句式 |

### 2.2 批量导入

- 支持 JSON 文件批量导入所有十类内容
- 导入时自动去重（单词按 `word`、短语按 `phrase` 唯一键 upsert；其余内容按 `title` 去重）
- 返回导入结果：成功数、跳过数、失败明细

### 2.3 每日学习计划

- 系统每天生成学习计划（可在配置文件中设置每日单词数、短语数、口语话题数）
- 计划来源：优先安排复习到期的内容，不足则从未学过的内容中补充
- 学习状态流转：`NEW` → `LEARNING` → `REVIEWING` → `MASTERED`

### 2.4 间隔复习（SM-2 算法）

复习间隔基于熟练度系数（`ease_factor`）动态计算，默认初始间隔序列：

| 复习次数 | 下次复习间隔 |
|----------|-------------|
| 第 1 次  | 1 天        |
| 第 2 次  | 3 天        |
| 第 3 次  | 7 天        |
| 第 4 次  | 15 天       |
| 第 5 次+ | 间隔 × ease_factor（默认 2.5） |

每次复习标记熟练程度（Again / Hard / Good / Easy），系统据此调整 `ease_factor` 和下次复习时间。

### 2.5 学习统计

- 每日新学 / 复习数量
- 连续学习天数（streak）
- 各状态内容数量分布（NEW / LEARNING / REVIEWING / MASTERED）
- 近 30 天学习趋势

---

## 三、数据库设计

### 3.1 `ielts_words`（雅思核心单词，跨技能）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | 主键 |
| `word` | VARCHAR(100) UNIQUE | 单词原形 |
| `phonetic_uk` | VARCHAR(100) | 英式音标 |
| `phonetic_us` | VARCHAR(100) | 美式音标 |
| `part_of_speech` | VARCHAR(20) | 词性（n./v./adj. 等） |
| `definition_zh` | TEXT | 中文释义（多义词换行分隔） |
| `definition_en` | TEXT | 英文释义 |
| `example_sentence` | TEXT | 例句（英文） |
| `example_translation` | TEXT | 例句翻译 |
| `frequency_level` | SMALLINT | 雅思出现频率（1-5，5最高） |
| `word_list` | VARCHAR(50) | 词表来源：AWL / GSL / IELTS |
| `difficulty` | SMALLINT | 难度（1基础 / 2中级 / 3高级，默认 2） |
| `skill_tags` | VARCHAR(100) | 适用技能，逗号分隔（listening,reading,writing,speaking） |
| `topic_tags` | VARCHAR(255) | 话题标签，逗号分隔（如 environment,health） |
| `created_at` | TIMESTAMPTZ | — |
| `updated_at` | TIMESTAMPTZ | — |

### 3.2 `ielts_phrases`（常用短语，跨技能）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | 主键 |
| `phrase` | VARCHAR(300) UNIQUE | 短语原文 |
| `meaning_zh` | TEXT | 中文含义 |
| `usage_note` | TEXT | 用法说明 |
| `example_sentence` | TEXT | 示例句 |
| `example_translation` | TEXT | 示例句翻译 |
| `category` | VARCHAR(50) | 类型：signal-word（听力信号词）/ sentence-frame（写作句型）/ collocation（搭配）/ connector（口语连接词）/ idiom |
| `difficulty` | SMALLINT | 难度（1基础 / 2中级 / 3高级，默认 2） |
| `skill_tags` | VARCHAR(100) | 适用技能（listening,reading,writing,speaking） |
| `topic_tags` | VARCHAR(255) | 话题标签 |
| `created_at` | TIMESTAMPTZ | — |
| `updated_at` | TIMESTAMPTZ | — |

### 3.3 `ielts_paraphrase_groups`（同义替换组 — 跨技能）

阅读判断题/匹配题、听力填空的核心考点；写作词汇多样性、口语换词能力的训练素材。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | 主键 |
| `group_name` | VARCHAR(200) | 组名（通常为核心概念，如 "increase"、"important"） |
| `core_expression` | VARCHAR(200) | 核心表达（原词或原短语） |
| `synonyms` | TEXT | 全部同义替换词/短语，每行一条，可含词性标注（如 `rise (v.)`） |
| `usage_note` | TEXT | 用法区别说明（区分语境、正式程度、搭配差异） |
| `example_original` | TEXT | 原句（含核心表达） |
| `example_paraphrased` | TEXT | 改写后的句子（使用同义替换） |
| `difficulty` | SMALLINT | 难度（1基础 / 2中级 / 3高级，默认 2） |
| `skill_tags` | VARCHAR(100) | 适用技能（通常为 listening,reading,writing,speaking） |
| `topic_tags` | VARCHAR(255) | 话题标签 |
| `created_at` | TIMESTAMPTZ | — |
| `updated_at` | TIMESTAMPTZ | — |

### 3.4 `ielts_pronunciation_points`（发音要点 — 听力 / 口语）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | 主键 |
| `title` | VARCHAR(200) | 要点标题（如"辅音+元音连读规则"） |
| `category` | VARCHAR(50) | 分类：stress（重音）/ linking（连读）/ weak-form（弱读）/ intonation（语调）/ elision（省音）/ assimilation（同化） |
| `explanation_zh` | TEXT | 中文讲解 |
| `rule_summary` | TEXT | 规则要点（简明列点，换行分隔） |
| `examples` | TEXT | 例词 / 例句（含音标标注，换行分隔） |
| `common_mistakes` | TEXT | 中国学习者常见错误及纠正 |
| `skill_tags` | VARCHAR(100) | 适用技能（listening,speaking） |
| `difficulty` | SMALLINT | 难度（1-3） |
| `created_at` | TIMESTAMPTZ | — |
| `updated_at` | TIMESTAMPTZ | — |

### 3.5 `ielts_grammar_points`（语法要点 — Grammar）

雅思写作和口语均对"语法范围与准确性"单独评分，语法要点是独立的学习单元。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | 主键 |
| `title` | VARCHAR(200) | 语法要点标题（如"现在完成时的用法"） |
| `category` | VARCHAR(50) | 语法分类：tense（时态）/ conditional（条件句）/ passive（被动语态）/ relative-clause（定语从句）/ modal（情态动词）/ comparison（比较级）/ article（冠词）/ sentence-structure（句型） |
| `explanation_zh` | TEXT | 中文讲解（规则说明） |
| `explanation_en` | TEXT | 英文讲解（适合对照学习） |
| `key_rules` | TEXT | 核心规则提炼（换行分隔，简明列点） |
| `examples` | TEXT | 典型例句（英文 + 中文翻译，换行分隔） |
| `common_errors` | TEXT | 中国学习者常见错误及纠正（换行分隔） |
| `difficulty` | SMALLINT | 难度（1-3） |
| `skill_tags` | VARCHAR(100) | 适用技能（writing,speaking） |
| `topic_tags` | VARCHAR(255) | 相关话题标签 |
| `created_at` | TIMESTAMPTZ | — |
| `updated_at` | TIMESTAMPTZ | — |

### 3.6 `ielts_grammar_exercises`（语法练习 — Grammar）

每道练习题关联一个语法要点，支持多种题型。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | 主键 |
| `grammar_point_id` | UUID FK | 关联 ielts_grammar_points（可为 null，表示综合练习） |
| `question_type` | VARCHAR(30) | 题型：fill-in-blank（填空）/ error-correction（改错）/ sentence-transformation（句子转换）/ multiple-choice（选择） |
| `question` | TEXT | 题目（含说明和句子） |
| `options` | TEXT | 选项（仅 multiple-choice，换行分隔） |
| `answer` | TEXT | 标准答案 |
| `explanation` | TEXT | 解析（说明为什么对/错，关联规则） |
| `difficulty` | SMALLINT | 难度（1-3） |
| `created_at` | TIMESTAMPTZ | — |
| `updated_at` | TIMESTAMPTZ | — |

### 3.7 `ielts_speaking_topics`（口语话题 — Speaking）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | 主键 |
| `title` | VARCHAR(200) | 话题标题 |
| `part` | SMALLINT | 口语考试 Part（1/2/3） |
| `question` | TEXT | 具体问题（Part 2 为完整 Cue Card） |
| `reference_answer` | TEXT | 参考答案思路或范例 |
| `key_vocabulary` | TEXT | 话题高分词汇（换行分隔） |
| `useful_phrases` | TEXT | 话题常用句式（换行分隔） |
| `difficulty` | SMALLINT | 难度（1基础 / 2中级 / 3高级，默认 2） |
| `topic_tags` | VARCHAR(255) | 话题标签（lifestyle,technology,environment…） |
| `created_at` | TIMESTAMPTZ | — |
| `updated_at` | TIMESTAMPTZ | — |

### 3.8 `ielts_listening_items`（听力练习 — Listening）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | 主键 |
| `title` | VARCHAR(200) | 练习标题（如"机场问询 Section 1"） |
| `section` | SMALLINT | Section 编号（1-4） |
| `question_type` | VARCHAR(50) | 题型：form-completion / note-completion / table-completion / multiple-choice / matching / map-labelling / flow-chart |
| `context_description` | TEXT | 听力场景描述（谁在哪里说什么） |
| `script_excerpt` | TEXT | 关键脚本片段（含信号词、答案位置提示） |
| `questions` | TEXT | 题目（换行分隔或小序号列表） |
| `answers` | TEXT | 答案及解析 |
| `tips` | TEXT | 该题型专项作答技巧 |
| `difficulty` | SMALLINT | 难度（1-3） |
| `topic_tags` | VARCHAR(255) | 话题标签 |
| `created_at` | TIMESTAMPTZ | — |
| `updated_at` | TIMESTAMPTZ | — |

### 3.9 `ielts_reading_items`（阅读练习 — Reading）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | 主键 |
| `title` | VARCHAR(300) | 文章标题 |
| `training_type` | VARCHAR(20) | 考试类型：ACADEMIC / GENERAL |
| `difficulty` | SMALLINT | 难度对应顺序（1-3） |
| `passage_text` | TEXT | 文章正文 |
| `question_type` | VARCHAR(50) | 题型：tfng（True/False/Not Given）/ ynng（Yes/No/Not Given）/ matching-headings / matching-info / sentence-completion / multiple-choice / summary-completion / short-answer |
| `questions` | TEXT | 题目（换行或序号分隔） |
| `answers` | TEXT | 答案及定位解析 |
| `key_vocabulary` | TEXT | 文章核心词汇及释义 |
| `tips` | TEXT | 该题型阅读技巧 |
| `topic_tags` | VARCHAR(255) | 话题标签 |
| `created_at` | TIMESTAMPTZ | — |
| `updated_at` | TIMESTAMPTZ | — |

### 3.10 `ielts_writing_tasks`（写作题目 — Writing）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | 主键 |
| `title` | VARCHAR(200) | 题目简短描述 |
| `task_number` | SMALLINT | Task 编号（1 或 2） |
| `training_type` | VARCHAR(20) | ACADEMIC / GENERAL |
| `task_type` | VARCHAR(50) | Task 1：bar-chart / line-graph / pie-chart / table / diagram / map / letter；Task 2：argument / discussion / problem-solution / two-part |
| `prompt` | TEXT | 完整题目要求 |
| `image_description` | TEXT | Task 1 图表数据描述（文字还原图表内容） |
| `model_answer` | TEXT | 范文 |
| `band_score_note` | TEXT | 高分答案评分要点（词汇、连贯、语法、任务回应） |
| `key_phrases` | TEXT | 该题型常用句式（换行分隔） |
| `difficulty` | SMALLINT | 难度（1基础 / 2中级 / 3高级，默认 2） |
| `topic_tags` | VARCHAR(255) | 话题标签 |
| `created_at` | TIMESTAMPTZ | — |
| `updated_at` | TIMESTAMPTZ | — |

### 3.11 `ielts_study_records`（学习记录，每条内容唯一）

无用户维度，直接按内容唯一。`content_type` 枚举涵盖全部十类内容。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | 主键 |
| `content_type` | VARCHAR(30) | `WORD` / `PHRASE` / `PARAPHRASE` / `PRONUNCIATION` / `GRAMMAR_POINT` / `GRAMMAR_EXERCISE` / `SPEAKING` / `LISTENING` / `READING` / `WRITING` |
| `content_id` | UUID | 对应内容 ID |
| `status` | VARCHAR(20) | `LEARNING` / `REVIEWING` / `MASTERED` |
| `ease_factor` | DECIMAL(4,2) | 难易系数（初始 2.50） |
| `interval_days` | INT | 当前复习间隔天数 |
| `repetition_count` | INT | 累计复习次数 |
| `next_review_at` | DATE | 下次复习日期 |
| `last_reviewed_at` | TIMESTAMPTZ | 最后复习时间 |
| `created_at` | TIMESTAMPTZ | 首次加入学习时间 |

**唯一约束**：`(content_type, content_id)`

### 3.12 `ielts_review_logs`（每次复习操作日志）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | 主键 |
| `record_id` | UUID FK | 关联 ielts_study_records（ON DELETE CASCADE） |
| `rating` | VARCHAR(10) | 本次评分：`AGAIN` / `HARD` / `GOOD` / `EASY` |
| `reviewed_at` | TIMESTAMPTZ | 复习时间 |

### 3.13 `ielts_daily_plans`（每日学习计划，每天唯一）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | 主键 |
| `plan_date` | DATE UNIQUE | 计划日期 |
| `total_items` | INT | 计划学习总数 |
| `completed_items` | INT | 已完成数（默认 0） |
| `generated_at` | TIMESTAMPTZ | 计划生成时间 |

---

## 四、API 设计

所有接口路径前缀：`/api/v1/ielts`，**无需认证，直接访问**。

### 4.1 单词管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/words` | 分页查询单词（支持关键词、分类、频率级别、标签筛选） |
| `GET` | `/words/{id}` | 获取单词详情 |
| `POST` | `/words` | 新增单词 |
| `PUT` | `/words/{id}` | 更新单词 |
| `DELETE` | `/words/{id}` | 删除单词 |
| `POST` | `/words/import` | 批量导入（CSV 或 JSON） |

### 4.2 短语管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/phrases` | 分页查询短语 |
| `GET` | `/phrases/{id}` | 短语详情 |
| `POST` | `/phrases` | 新增 |
| `PUT` | `/phrases/{id}` | 更新 |
| `DELETE` | `/phrases/{id}` | 删除 |
| `POST` | `/phrases/import` | 批量导入 |

### 4.3 同义替换管理（Paraphrase）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/paraphrase-groups` | 分页查询（支持关键词、技能标签、话题标签筛选） |
| `GET` | `/paraphrase-groups/{id}` | 替换组详情 |
| `POST` | `/paraphrase-groups` | 新增 |
| `PUT` | `/paraphrase-groups/{id}` | 更新 |
| `DELETE` | `/paraphrase-groups/{id}` | 删除 |
| `POST` | `/paraphrase-groups/import` | 批量导入 |

### 4.4 发音要点管理（Pronunciation）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/pronunciation-points` | 分页查询（支持 category / difficulty 筛选） |
| `GET` | `/pronunciation-points/{id}` | 发音要点详情 |
| `POST` | `/pronunciation-points` | 新增 |
| `PUT` | `/pronunciation-points/{id}` | 更新 |
| `DELETE` | `/pronunciation-points/{id}` | 删除 |
| `POST` | `/pronunciation-points/import` | 批量导入 |

### 4.5 语法管理（Grammar）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/grammar-points` | 分页查询语法要点（支持 category / difficulty / skill_tags 筛选） |
| `GET` | `/grammar-points/{id}` | 语法要点详情（含关联练习题列表） |
| `POST` | `/grammar-points` | 新增语法要点 |
| `PUT` | `/grammar-points/{id}` | 更新语法要点 |
| `DELETE` | `/grammar-points/{id}` | 删除语法要点（级联删除关联练习题） |
| `POST` | `/grammar-points/import` | 批量导入语法要点 |
| `GET` | `/grammar-exercises` | 分页查询语法练习（支持 grammar_point_id / question_type / difficulty 筛选） |
| `GET` | `/grammar-exercises/{id}` | 练习题详情 |
| `POST` | `/grammar-exercises` | 新增练习题 |
| `PUT` | `/grammar-exercises/{id}` | 更新练习题 |
| `DELETE` | `/grammar-exercises/{id}` | 删除练习题 |
| `POST` | `/grammar-exercises/import` | 批量导入练习题 |

### 4.6 口语话题管理（Speaking）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/speaking-topics` | 分页查询（支持 Part / 标签筛选） |
| `GET` | `/speaking-topics/{id}` | 话题详情 |
| `POST` | `/speaking-topics` | 新增 |
| `PUT` | `/speaking-topics/{id}` | 更新 |
| `DELETE` | `/speaking-topics/{id}` | 删除 |
| `POST` | `/speaking-topics/import` | 批量导入 |

### 4.7 听力练习管理（Listening）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/listening-items` | 分页查询（支持 section / 题型 / 难度筛选） |
| `GET` | `/listening-items/{id}` | 练习详情 |
| `POST` | `/listening-items` | 新增 |
| `PUT` | `/listening-items/{id}` | 更新 |
| `DELETE` | `/listening-items/{id}` | 删除 |
| `POST` | `/listening-items/import` | 批量导入 |

### 4.8 阅读练习管理（Reading）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/reading-items` | 分页查询（支持 training-type / 题型 / 难度筛选） |
| `GET` | `/reading-items/{id}` | 练习详情 |
| `POST` | `/reading-items` | 新增 |
| `PUT` | `/reading-items/{id}` | 更新 |
| `DELETE` | `/reading-items/{id}` | 删除 |
| `POST` | `/reading-items/import` | 批量导入 |

### 4.9 写作题目管理（Writing）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/writing-tasks` | 分页查询（支持 task-number / task-type / training-type 筛选） |
| `GET` | `/writing-tasks/{id}` | 题目详情 |
| `POST` | `/writing-tasks` | 新增 |
| `PUT` | `/writing-tasks/{id}` | 更新 |
| `DELETE` | `/writing-tasks/{id}` | 删除 |
| `POST` | `/writing-tasks/import` | 批量导入 |

### 4.10 学习计划与复习

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/study/today` | 获取今日学习计划（待复习优先，不足补充新内容；可按技能筛选） |
| `POST` | `/study/review` | 提交复习结果（recordId + rating） |
| `GET` | `/study/records` | 全部学习记录（支持 content_type / status 筛选、分页） |
| `GET` | `/study/stats` | 学习统计（streak、按技能的状态分布、近30天趋势） |

### 4.11 导入格式说明（JSON）

所有内容统一使用 JSON 批量导入，字段与数据库字段对应（camelCase）。

**单词示例**：
```json
[
  {
    "word": "abandon",
    "phoneticUk": "/əˈbændən/",
    "phoneticUs": "/əˈbændən/",
    "partOfSpeech": "v.",
    "definitionZh": "放弃；抛弃",
    "definitionEn": "to leave someone or something permanently",
    "exampleSentence": "They had to abandon the car in the snow.",
    "exampleTranslation": "他们不得不把车丢在雪地里。",
    "frequencyLevel": 4,
    "wordList": "AWL",
    "skillTags": "reading,writing",
    "topicTags": "environment,society"
  }
]
```

**听力练习示例**：
```json
[
  {
    "title": "机场问询 Section 1",
    "section": 1,
    "questionType": "form-completion",
    "contextDescription": "一名旅客向机场服务台工作人员咨询行李寄存事宜。",
    "scriptExcerpt": "Staff: ...the storage fee is five pounds per item per day...",
    "questions": "1. Storage fee per item: £_____ per day\n2. Payment method accepted: _____",
    "answers": "1. five / 5\n2. credit card",
    "tips": "Section 1 为双人对话，答案通常直接读出，注意数字和拼写。",
    "difficulty": 1,
    "topicTags": "travel,airport"
  }
]
```

**写作题目示例**：
```json
[
  {
    "title": "柱状图-城市交通方式变化",
    "taskNumber": 1,
    "trainingType": "ACADEMIC",
    "taskType": "bar-chart",
    "prompt": "The chart below shows the percentage of people using different forms of transport in a city in 1990 and 2020.",
    "imageDescription": "柱状图显示1990年和2020年该城市私家车使用率从30%升至55%，公共交通从50%降至35%，自行车基本持平约15%。",
    "modelAnswer": "The bar chart compares the proportion of residents using three modes of transport...",
    "bandScoreNote": "需覆盖所有主要趋势并做对比；使用 while/whereas 等对比连接词；使用 rose significantly / remained stable 等变化动词。",
    "keyPhrases": "rose significantly from ... to ...\nshowed a marked decline\nremained relatively stable at\nBy contrast / In comparison",
    "topicTags": "transport,urban"
  }
]
```

---

## 五、模块内部结构

```
kb-ielts/
├── pom.xml                          ← 含 spring-boot-maven-plugin repackage，可独立打包运行
└── src/main/
    ├── java/com/enterprise/kb/ielts/
    │   ├── IeltsApplication.java        ← @SpringBootApplication 独立启动入口
    │   ├── controller/
    │   │   ├── IeltsWordController.java
    │   │   ├── IeltsPhraseController.java
    │   │   ├── IeltsParaphraseGroupController.java
    │   │   ├── IeltsPronunciationPointController.java
    │   │   ├── IeltsGrammarPointController.java
    │   │   ├── IeltsGrammarExerciseController.java
    │   │   ├── IeltsSpeakingTopicController.java
    │   │   ├── IeltsListeningItemController.java
    │   │   ├── IeltsReadingItemController.java
    │   │   ├── IeltsWritingTaskController.java
    │   │   └── IeltsStudyController.java
    │   ├── service/
    │   │   ├── IeltsWordService.java
    │   │   ├── IeltsPhraseService.java
    │   │   ├── IeltsParaphraseGroupService.java
    │   │   ├── IeltsPronunciationPointService.java
    │   │   ├── IeltsGrammarPointService.java
    │   │   ├── IeltsGrammarExerciseService.java
    │   │   ├── IeltsSpeakingTopicService.java
    │   │   ├── IeltsListeningItemService.java
    │   │   ├── IeltsReadingItemService.java
    │   │   ├── IeltsWritingTaskService.java
    │   │   ├── IeltsStudyService.java
    │   │   └── impl/
    │   │       ├── IeltsWordServiceImpl.java
    │   │       ├── IeltsPhraseServiceImpl.java
    │   │       ├── IeltsParaphraseGroupServiceImpl.java
    │   │       ├── IeltsPronunciationPointServiceImpl.java
    │   │       ├── IeltsGrammarPointServiceImpl.java
    │   │       ├── IeltsGrammarExerciseServiceImpl.java
    │   │       ├── IeltsSpeakingTopicServiceImpl.java
    │   │       ├── IeltsListeningItemServiceImpl.java
    │   │       ├── IeltsReadingItemServiceImpl.java
    │   │       ├── IeltsWritingTaskServiceImpl.java
    │   │       └── IeltsStudyServiceImpl.java
    │   ├── mapper/
    │   │   ├── IeltsWordMapper.java
    │   │   ├── IeltsPhraseMapper.java
    │   │   ├── IeltsParaphraseGroupMapper.java
    │   │   ├── IeltsPronunciationPointMapper.java
    │   │   ├── IeltsGrammarPointMapper.java
    │   │   ├── IeltsGrammarExerciseMapper.java
    │   │   ├── IeltsSpeakingTopicMapper.java
    │   │   ├── IeltsListeningItemMapper.java
    │   │   ├── IeltsReadingItemMapper.java
    │   │   ├── IeltsWritingTaskMapper.java
    │   │   ├── IeltsStudyRecordMapper.java
    │   │   ├── IeltsReviewLogMapper.java
    │   │   └── IeltsDailyPlanMapper.java
    │   ├── model/
    │   │   ├── IeltsWord.java
    │   │   ├── IeltsPhrase.java
    │   │   ├── IeltsParaphraseGroup.java
    │   │   ├── IeltsPronunciationPoint.java
    │   │   ├── IeltsGrammarPoint.java
    │   │   ├── IeltsGrammarExercise.java
    │   │   ├── IeltsSpeakingTopic.java
    │   │   ├── IeltsListeningItem.java
    │   │   ├── IeltsReadingItem.java
    │   │   ├── IeltsWritingTask.java
    │   │   ├── IeltsStudyRecord.java
    │   │   ├── IeltsReviewLog.java
    │   │   └── IeltsDailyPlan.java
    │   ├── dto/
    │   │   ├── IeltsWordDto.java
    │   │   ├── IeltsPhraseDto.java
    │   │   ├── IeltsParaphraseGroupDto.java
    │   │   ├── IeltsPronunciationPointDto.java
    │   │   ├── IeltsGrammarPointDto.java
    │   │   ├── IeltsGrammarExerciseDto.java
    │   │   ├── IeltsSpeakingTopicDto.java
    │   │   ├── IeltsListeningItemDto.java
    │   │   ├── IeltsReadingItemDto.java
    │   │   ├── IeltsWritingTaskDto.java
    │   │   ├── IeltsStudyItemDto.java       ← 今日计划中的每一项（含内容类型 + 快照）
    │   │   ├── IeltsReviewRequest.java      ← 提交复习结果入参
    │   │   ├── IeltsStudyStatsDto.java      ← 统计数据（含按技能细分）
    │   │   └── IeltsImportResult.java       ← 批量导入结果
    │   └── util/
    │       └── SpacedRepetitionCalculator.java  ← SM-2 间隔计算
    └── resources/
        ├── application.yml              ← 独立配置，端口 8082，只含 PostgreSQL + MyBatis
        ├── db/changelog/
        │   ├── db.changelog-master.xml
        │   └── 001-create-ielts-tables.sql  ← 13 张表的 DDL
        ├── mapper/
        │   ├── IeltsWordMapper.xml
        │   ├── IeltsPhraseMapper.xml
        │   ├── IeltsParaphraseGroupMapper.xml
        │   ├── IeltsPronunciationPointMapper.xml
        │   ├── IeltsGrammarPointMapper.xml
        │   ├── IeltsGrammarExerciseMapper.xml
        │   ├── IeltsSpeakingTopicMapper.xml
        │   ├── IeltsListeningItemMapper.xml
        │   ├── IeltsReadingItemMapper.xml
        │   ├── IeltsWritingTaskMapper.xml
        │   ├── IeltsStudyRecordMapper.xml
        │   ├── IeltsReviewLogMapper.xml
        │   └── IeltsDailyPlanMapper.xml
        └── static/
            ├── index.html               ← 首页 / 仪表盘
            ├── study.html               ← 今日学习（翻卡复习）
            ├── words.html               ← 单词管理
            ├── phrases.html             ← 短语管理
            ├── paraphrase.html          ← 同义替换组管理
            ├── pronunciation.html       ← 发音要点管理
            ├── grammar.html             ← 语法要点 + 练习题
            ├── speaking.html            ← 口语话题
            ├── listening.html           ← 听力练习
            ├── reading.html             ← 阅读练习
            ├── writing.html             ← 写作题目
            ├── import.html              ← 批量导入
            ├── stats.html               ← 学习统计
            └── assets/
                ├── css/
                │   └── ielts.css        ← 侧边栏、翻卡动画、热力图等专属样式
                └── js/
                    ├── api.js           ← fetch 封装，BASE_URL=/api/v1/ielts
                    ├── nav.js           ← 侧边栏高亮、通用初始化
                    └── study-card.js    ← 翻卡复习核心逻辑
```

---

## 六、开发阶段拆分

### Phase 1 — 模块骨架 + 数据层

- [ ] 创建 `kb-ielts` Maven 模块，`pom.xml` 仅依赖 `kb-common` + MyBatis + web，配置 `spring-boot-maven-plugin` repackage
- [ ] 根 `pom.xml` 新增模块声明 + `dependencyManagement` 条目（**不**修改 kb-app 依赖）
- [ ] 编写 `IeltsApplication.java`（独立 main class，端口 8082）
- [ ] 编写 `application.yml`（仅含 PostgreSQL、MyBatis、PageHelper，无 Redis/Milvus/AI）
- [ ] Liquibase 迁移：`001-create-ielts-tables.sql`（9 张表 + 索引）
- [ ] 实现全部 Model 类、Mapper 接口、Mapper XML（基础 CRUD）

### Phase 2 — 内容管理 API

- [ ] 十类内容（单词/短语/同义替换/发音/语法要点/语法练习/口语/听力/阅读/写作）的 Service + Controller（CRUD + 分页 + 筛选）
- [ ] 批量导入功能：JSON 解析 → upsert 事务写入 → 返回 `IeltsImportResult`

### Phase 3 — 学习核心逻辑

- [ ] `SpacedRepetitionCalculator`：SM-2 算法（ease_factor + interval 动态调整）
- [ ] `IeltsStudyService.getTodayPlan()`：到期复习优先，不足补充新内容，幂等生成 `ielts_daily_plans`
- [ ] `IeltsStudyService.submitReview()`：更新学习记录 + 写入复习日志 + 更新今日计划完成数
- [ ] `IeltsStudyService.getStats()`：streak 计算、状态分布、近30天趋势

### Phase 4 — 前端页面

- [ ] `assets/css/ielts.css`：侧边栏布局、翻卡 3D 动画、热力图样式
- [ ] `assets/js/api.js`：fetch 封装（统一错误提示、BASE_URL）
- [ ] `assets/js/nav.js`：侧边栏渲染、当前页高亮
- [ ] `assets/js/study-card.js`：翻卡队列逻辑、评分提交、进度更新
- [ ] `index.html`：仪表盘（今日进度、streak、各类型统计卡）
- [ ] `study.html`：今日学习（翻卡复习、Again/Hard/Good/Easy、完成页）
- [ ] `words.html` / `phrases.html` / `paraphrase.html` / `pronunciation.html`：列表 + 搜索 + 新增/编辑弹窗
- [ ] `grammar.html`：左右分栏（要点列表 + 详情 + 关联练习题）
- [ ] `speaking.html` / `listening.html` / `reading.html` / `writing.html`：卡片列表 + 展开详情
- [ ] `import.html`：Tab 选内容类型 + 拖拽上传 JSON + 结果展示
- [ ] `stats.html`：streak 热力图 + 状态分布条形图 + 近30天折线图（Chart.js）

### Phase 5 — 调试与种子数据

- [ ] 准备各类内容 JSON 种子文件（单词 100+、短语 50+、语法 20+、各技能 10+）
- [ ] 通过 `import.html` 批量导入初始化数据
- [ ] 端到端验证：导入 → 仪表盘刷新 → 今日学习翻卡 → 提交复习 → 统计变化

---

## 七、关键设计决策

| 决策点 | 选择 | 原因 |
|--------|------|------|
| 启动方式 | 独立 Spring Boot 进程（端口 8082） | 与知识库主应用完全解耦，可单独开发、部署、重启 |
| 认证方案 | 无认证 | 纯个人学习工具，完全独立于知识库用户体系 |
| 用户维度 | 不区分用户（单学习者） | 无 auth，无 user_id，所有记录全局唯一，逻辑最简 |
| SRS 算法 | SM-2 简化版 | 逻辑简单可控，参数可调，无外部依赖 |
| 听力/阅读/写作/口语 SRS | 仅有 `LEARNING` / `MASTERED` 两态，不做 ease_factor 计算 | 练习题不适合机械间隔，适合按需刷题；单词和短语才做完整 SM-2 |
| 导入幂等性 | 以 `word` / `phrase` 唯一键做 upsert | 避免重复，支持数据更新修正 |
| 内容与进度分离 | 内容表存共享数据，`study_records` 存进度 | 利于内容独立维护，进度独立迭代 |

---

## 八、DDL 参考（PostgreSQL）

```sql
-- 单词表
CREATE TABLE ielts_words (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    word                VARCHAR(100) NOT NULL UNIQUE,
    phonetic_uk         VARCHAR(100),
    phonetic_us         VARCHAR(100),
    part_of_speech      VARCHAR(20),
    definition_zh       TEXT,
    definition_en       TEXT,
    example_sentence    TEXT,
    example_translation TEXT,
    frequency_level     SMALLINT DEFAULT 3 CHECK (frequency_level BETWEEN 1 AND 5),
    word_list           VARCHAR(50),
    difficulty          SMALLINT DEFAULT 2 CHECK (difficulty BETWEEN 1 AND 3),
    skill_tags          VARCHAR(100),
    topic_tags          VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 短语表（跨技能）
CREATE TABLE ielts_phrases (
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

-- 同义替换组表（跨技能）
CREATE TABLE ielts_paraphrase_groups (
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

-- 发音要点表（听力 / 口语）
CREATE TABLE ielts_pronunciation_points (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title            VARCHAR(200) NOT NULL,
    category         VARCHAR(50) CHECK (category IN ('stress','linking','weak-form','intonation','elision','assimilation')),
    explanation_zh   TEXT,
    rule_summary     TEXT,
    examples         TEXT,
    common_mistakes  TEXT,
    skill_tags       VARCHAR(100),
    difficulty       SMALLINT DEFAULT 2 CHECK (difficulty BETWEEN 1 AND 3),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 语法要点表（Grammar）
CREATE TABLE ielts_grammar_points (
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

-- 语法练习表（Grammar Exercises）
CREATE TABLE ielts_grammar_exercises (
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
CREATE INDEX idx_grammar_exercises_point ON ielts_grammar_exercises (grammar_point_id);

-- 口语话题表（Speaking）
CREATE TABLE ielts_speaking_topics (
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

-- 听力练习表（Listening）
CREATE TABLE ielts_listening_items (
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

-- 阅读练习表（Reading）
CREATE TABLE ielts_reading_items (
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

-- 写作题目表（Writing）
CREATE TABLE ielts_writing_tasks (
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

-- 学习记录主表（每条内容唯一，无用户维度）
CREATE TABLE ielts_study_records (
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
CREATE INDEX idx_study_records_review ON ielts_study_records (next_review_at, status);
CREATE INDEX idx_study_records_type   ON ielts_study_records (content_type, status);

-- 复习操作日志
CREATE TABLE ielts_review_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    record_id   UUID NOT NULL REFERENCES ielts_study_records(id) ON DELETE CASCADE,
    rating      VARCHAR(10) NOT NULL CHECK (rating IN ('AGAIN','HARD','GOOD','EASY')),
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_review_logs_date ON ielts_review_logs (reviewed_at);

-- 每日计划（每天唯一一条）
CREATE TABLE ielts_daily_plans (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_date       DATE NOT NULL UNIQUE,
    total_items     INT NOT NULL DEFAULT 0,
    completed_items INT NOT NULL DEFAULT 0,
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## 九、前端页面设计

技术栈与 kb-app 保持一致：**Bootstrap 5.3 + Bootstrap Icons**，纯原生 HTML/CSS/JS，静态文件由 Spring Boot 直接托管（`src/main/resources/static/`）。

### 9.1 页面清单

| 页面文件 | 功能 |
|----------|------|
| `index.html` | 首页 / 仪表盘 |
| `study.html` | 今日学习（核心复习界面） |
| `words.html` | 单词管理 |
| `phrases.html` | 短语管理 |
| `paraphrase.html` | 同义替换组管理 |
| `pronunciation.html` | 发音要点管理 |
| `grammar.html` | 语法要点 + 练习题 |
| `speaking.html` | 口语话题 |
| `listening.html` | 听力练习 |
| `reading.html` | 阅读练习 |
| `writing.html` | 写作题目 |
| `import.html` | 批量导入 |
| `stats.html` | 学习统计 |

### 9.2 通用布局

所有页面采用两栏结构，与 kb-app 一致：

```
┌────────────────┬───────────────────────────────────────┐
│   侧边栏        │   主内容区                             │
│                │                                       │
│  IELTS 学习    │  顶部面包屑 / 操作栏                   │
│                │                                       │
│  ● 仪表盘      │  内容主体                             │
│  ● 今日学习    │                                       │
│  ─ 内容管理 ─  │                                       │
│  ○ 单词        │                                       │
│  ○ 短语        │                                       │
│  ○ 同义替换    │                                       │
│  ○ 发音要点    │                                       │
│  ○ 语法        │                                       │
│  ○ 口语        │                                       │
│  ○ 听力        │                                       │
│  ○ 阅读        │                                       │
│  ○ 写作        │                                       │
│  ─ 工具 ──────  │                                       │
│  ○ 批量导入    │                                       │
│  ○ 学习统计    │                                       │
└────────────────┴───────────────────────────────────────┘
```

### 9.3 各页面详细说明

#### `index.html` — 仪表盘
- **今日进度卡**：已完成 / 计划总数，进度条
- **连续学习天数（streak）**：大数字高亮展示
- **各技能内容数量**：8 个统计卡（单词/短语/语法点/语法题/口语/听力/阅读/写作），点击跳转对应管理页
- **快速入口**：「开始今日学习」按钮跳转 `study.html`

#### `study.html` — 今日学习（核心页面）
- **顶部进度**：`3 / 20` 已完成，进度条
- **内容卡片**（翻卡式）：
  - 正面：单词 / 短语 / 语法规则标题 / 练习题题干
  - 背面：释义 / 例句 / 答案 / 解析
  - 点击卡片或「查看答案」按钮翻转，CSS 3D flip 动画
- **评分按钮**（翻面后显示）：
  - `Again`（红）/ `Hard`（橙）/ `Good`（绿）/ `Easy`（蓝）
  - 单词/短语触发完整 SM-2，语法/口语等仅切换 MASTERED
- **内容类型角标**：左上角彩色标签（单词 / 短语 / 语法 / 口语 / 听力 / 阅读 / 写作）
- **完成状态**：全部完成后展示「今日任务完成」庆祝卡片，显示当天数据

#### `words.html` — 单词管理
- **搜索栏**：关键词输入 + 词表筛选（AWL/GSL/IELTS）+ 技能标签筛选 + 频率级别筛选
- **单词列表**：表格，列：单词 / 词性 / 中文释义（截断）/ 频率星级 / 词表 / 操作
- **操作**：查看详情弹窗（含音标、全部释义、例句）/ 编辑 / 删除 / 「加入今日学习」
- **新增**：右上角按钮打开表单弹窗
- **分页**：底部分页组件

#### `phrases.html` — 短语管理
- 结构同单词页，列：短语 / 类型 / 中文含义 / 技能标签 / 操作
- 详情弹窗含用法说明、示例句

#### `grammar.html` — 语法
- **左侧面板**：语法要点列表，按 category 分组（时态 / 条件句 / 被动 / …），点击高亮
- **右侧详情**：
  - 要点标题 + 分类标签
  - 中英文讲解（Tab 切换）
  - 核心规则（numbered list）
  - 典型例句
  - 常见错误（红色高亮错误部分，绿色显示正确形式）
  - 关联练习题列表（题干 + 展开查看答案）
- **新增/编辑**：弹窗表单

#### `speaking.html` / `listening.html` / `reading.html` / `writing.html`
- 各自展示对应内容的列表（卡片或表格）
- 筛选栏（Part/Section/题型/Task 类型等）
- 点击卡片展开详情（同页展开 或 弹窗）
- 新增 / 编辑 / 删除操作

#### `import.html` — 批量导入
- **内容类型选择**：Tab 或下拉（单词 / 短语 / 语法要点 / 语法练习 / 口语 / 听力 / 阅读 / 写作）
- **JSON 格式提示**：每种类型显示对应的 JSON 字段示例（可折叠）
- **上传区域**：拖拽或点击选择 `.json` 文件
- **导入结果**：成功 N 条 / 跳过 N 条 / 失败明细列表

#### `stats.html` — 学习统计
- **Streak 展示**：连续天数 + 日历热力图（近 30 天，深浅色区分有无学习）
- **各类型状态分布**：堆叠条形图（LEARNING / REVIEWING / MASTERED）
- **近 30 天趋势**：折线图（每日新学 + 复习数量），使用 Chart.js

### 9.4 静态资源结构

```
static/
├── index.html
├── study.html
├── words.html
├── phrases.html
├── grammar.html
├── speaking.html
├── listening.html
├── reading.html
├── writing.html
├── import.html
├── stats.html
└── assets/
    ├── css/
    │   └── ielts.css        ← 侧边栏、卡片、翻卡动画、热力图等专属样式
    └── js/
        ├── api.js           ← fetch 封装，统一错误处理，BASE_URL = /api/v1/ielts
        ├── nav.js           ← 侧边栏高亮、通用页面初始化
        └── study-card.js    ← 翻卡复习核心逻辑（卡片队列、评分提交、进度更新）
```

### 9.5 翻卡复习交互流程

```
GET /study/today
      ↓
  卡片队列初始化（按 content_type 拼装卡片）
      ↓
  显示卡片正面
      ↓
  用户点击「查看答案」
      ↓
  翻转动画 → 显示背面（答案/释义/解析）
      ↓
  显示 Again / Hard / Good / Easy 按钮
      ↓
  POST /study/review { recordId, rating }
      ↓
  加载下一张卡片（或显示完成页面）
```

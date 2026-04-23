package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.document.service.ChunkingService;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * 句子边界对齐的文档分块服务。
 *
 * <p>在 {@link ChunkingServiceImpl} 的 token 滑动窗口基础上，进一步将每个 chunk
 * 的起点对齐到句子边界：解码 overlap 区域文本，找到第一个句子结束符，
 * 从其后开始作为下一个 chunk 的起点，避免 chunk 从句子中间切入。</p>
 *
 * <p>若 overlap 区域内未找到句子边界，则退化为普通 token 级 overlap（与
 * {@link ChunkingServiceImpl} 行为一致）。</p>
 *
 * <p>支持中英文句子边界：{@code 。！？\n} 和 {@code . ! ?}（后跟空格时才视为句末）。</p>
 */
@Slf4j
@Primary
@Service("sentenceAwareChunkingService")
public class SentenceAwareChunkingServiceImpl implements ChunkingService {

    @Value("${enterprise.kb.chunking.chunk-size:512}")
    private int chunkSize;

    @Value("${enterprise.kb.chunking.overlap-tokens:100}")
    private int overlapTokens;

    private static final int MIN_CHUNK_LENGTH = 5;

    private final Encoding encoding;

    public SentenceAwareChunkingServiceImpl() {
        // CL100K_BASE 是 GPT-4 / text-embedding 系列使用的 tokenizer，与 DashScope 兼容
        this.encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
    }

    /**
     * 将文档列表分块，chunk 起点对齐到句子边界。
     *
     * @param documents  源文档列表
     * @param documentId 父文档 UUID
     * @param spaceId    空间 UUID
     * @return 分块后的文档列表
     */
    @Override
    public List<Document> chunk(List<Document> documents, UUID documentId, UUID spaceId) {
        List<Document> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (Document doc : documents) {
            // 将整篇文档文本编码为 token id 列表
            // 例："机器学习是..." → [15285, 2627, 37029, ...]
            IntArrayList tokens = encoding.encode(doc.getText());

            // start 是当前 chunk 的起始 token 下标，每轮循环推进一个 chunk
            int start = 0;

            while (start < tokens.size()) {
                // end 是当前 chunk 的结束下标（不含），最多取 chunkSize 个 token
                // 最后一段不足 chunkSize 时取到文档末尾
                int end = Math.min(start + chunkSize, tokens.size());

                // 截取 [start, end) 范围内的 token，解码回文本作为本 chunk 内容
                String chunkText = encoding.decode(slice(tokens, start, end)).trim();

                // 过滤极短残片（通常只在文档末尾出现），避免无意义内容入库
                if (chunkText.length() > MIN_CHUNK_LENGTH) {
                    // 保留原文档的元数据（如文件名、页码等），再追加分块专属字段
                    Document chunk = new Document(chunkText, new HashMap<>(doc.getMetadata()));
                    chunk.getMetadata().put("documentId", documentId.toString());
                    chunk.getMetadata().put("spaceId", spaceId.toString());
                    chunk.getMetadata().put("chunkIndex", chunkIndex++);
                    chunks.add(chunk);
                }

                // 已到文档末尾，退出循环
                if (end >= tokens.size()) break;

                // 计算下一个 chunk 的起始位置，尽量对齐到句子边界
                start = nextSentenceAlignedStart(tokens, start, end);
            }
        }

        log.debug("Split {} documents into {} chunks (chunkSize={}, overlapTokens={}) for documentId={}",
                documents.size(), chunks.size(), chunkSize, overlapTokens, documentId);
        return chunks;
    }

    /**
     * 计算下一个 chunk 的起始 token 位置，尽量对齐到句子边界。
     *
     * <p>整体思路：
     * <pre>
     * tokens: [0 ........... overlapStart ........... currentEnd ........... ]
     *                            ↑
     *                      overlap 区域起点 = currentStart + chunkSize - overlapTokens
     *
     * 1. 解码 [overlapStart, currentEnd) 这段 overlap 区域的文本
     * 2. 在该文本中找第一个句子结束符（。！？\n 等）
     * 3. 将结束符之前的文本重新 encode，得到需要跳过的 token 数
     * 4. 下一个 chunk 从 overlapStart + tokensBeforeBoundary 开始
     *    → 即从句子结束符之后的第一个完整句子开头切入
     * </pre>
     * </p>
     *
     * <p>举例（chunkSize=10, overlapTokens=3）：
     * <pre>
     * 原始 tokens:  [0,1,2,3,4,5,6,7,8,9, 10,11,12,13,14...]
     * 第1个 chunk:  [0..9]  → decode → "...完整内容...机器学习是"
     * overlapStart = 0 + 10 - 3 = 7
     * overlap区域: [7,8,9] → decode → "机器学习是"  （无句子边界）
     * → 退化为 token 级 overlap，下一个 chunk 从 token[7] 开始
     *
     * 若 overlap区域: [7,8,9] → decode → "完毕。下一章"
     *                                          ↑ 找到句子边界，位置在"。"之后
     * → encode("完毕。") = 2 个 token
     * → 下一个 chunk 从 token[7+2=9] 开始，即从"下一章"切入，句子完整
     * </pre>
     * </p>
     */
    private int nextSentenceAlignedStart(IntArrayList tokens, int currentStart, int currentEnd) {
        // overlap 区域的起始 token 下标
        // 公式：当前 chunk 起点 + chunk 大小 - overlap 大小
        // 含义：从当前 chunk 末尾往前退 overlapTokens 个 token
        int overlapStart = currentStart + chunkSize - overlapTokens;

        // 防御性检查：若 overlapTokens >= chunkSize，overlapStart 会 <= currentStart
        // 此时直接从 currentEnd 继续，避免死循环
        if (overlapStart >= currentEnd) {
            return currentEnd;
        }

        // 解码 overlap 区域文本，用于查找句子边界
        // 只解码这一小段（约 overlapTokens 个 token），开销远小于解码整段文档
        String overlapText = encoding.decode(slice(tokens, overlapStart, currentEnd));

        // 在 overlap 文本中找第一个句子结束符，返回结束符之后的字符位置
        int boundaryEnd = findFirstSentenceBoundary(overlapText);

        if (boundaryEnd < 0) {
            // overlap 区域内没有句子边界（如连续的数字、代码段等）
            // 退化为普通 token 级 overlap：直接从 overlapStart 开始下一个 chunk
            log.debug("No sentence boundary found in overlap region, falling back to token-based overlap");
            return overlapStart;
        }

        // 将句子边界前的文本重新 encode，得到需要跳过的 token 数
        // 例：overlapText = "完毕。下一章"，boundaryEnd = 3（"完毕。"的长度）
        //     encode("完毕。") = 2 个 token → 跳过 2 个
        int tokensBeforeBoundary = encoding.encode(overlapText.substring(0, boundaryEnd)).size();

        // 下一个 chunk 从句子结束符之后开始，保证 chunk 起点是完整句子的开头
        return overlapStart + tokensBeforeBoundary;
    }

    /**
     * 在文本中找到第一个句子结束符，返回其后的字符位置（即下一句的起点索引）。
     * 未找到时返回 -1。
     *
     * <p>判断规则：
     * <ul>
     *   <li>中文句末：{@code 。！？} → 直接视为句子结束</li>
     *   <li>换行符：{@code \n} → 视为段落结束</li>
     *   <li>英文句末：{@code . ! ?} → 必须后跟空格，避免误判小数点和缩写（如 "Dr."）</li>
     * </ul>
     * </p>
     *
     * <p>返回值是结束符之后一位的索引，可直接用于 {@code text.substring(returnValue)}
     * 获取下一句内容。</p>
     */
    private int findFirstSentenceBoundary(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // 中文句末标点和换行：直接返回标点之后的位置
            if (c == '。' || c == '！' || c == '？' || c == '\n') {
                return i + 1;
            }

            // 英文句末标点：必须后跟空格，防止误判 "3.14"、"Dr." 等
            // 返回标点和空格之后的位置（跳过空格，下一句从非空白字符开始）
            if ((c == '.' || c == '!' || c == '?') && i + 1 < text.length() && text.charAt(i + 1) == ' ') {
                return i + 2;
            }
        }
        return -1;
    }

    /**
     * 截取 token 列表的 [from, to) 子段，返回新的 IntArrayList。
     *
     * @param tokens 原始 token 列表
     * @param from   起始下标（含）
     * @param to     结束下标（不含）
     * @return 子段的新 IntArrayList
     */
    private IntArrayList slice(IntArrayList tokens, int from, int to) {
        IntArrayList result = new IntArrayList(to - from);
        for (int i = from; i < to; i++) {
            result.add(tokens.get(i));
        }
        return result;
    }
}

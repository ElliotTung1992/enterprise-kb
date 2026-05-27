package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.document.mapper.MdChildChunkMapper;
import com.enterprise.kb.document.mapper.MdDocumentMapper;
import com.enterprise.kb.document.mapper.MdParentChunkMapper;
import com.enterprise.kb.document.model.MdChildChunk;
import com.enterprise.kb.document.model.MdParentChunk;
import com.enterprise.kb.search.dto.SearchHit;
import com.enterprise.kb.search.service.MdParentExpansionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Markdown parent 展开服务实现。
 *
 * <p>将 child 命中按 parentId 分组（保留 rerank 顺序），每个 parent 收集其全部命中 child 的 seq。
 * parent 整段不超上限时直接整段返回；超限时按命中 seq 的邻近度分簇，每簇围绕命中开窗，
 * 簇之间以「……（中间略）……」省略，从而支持「同一 section 命中首尾多处」的多窗节选。</p>
 */
@Service
@RequiredArgsConstructor
public class MdParentExpansionServiceImpl implements MdParentExpansionService {

    private final MdChildChunkMapper childChunkMapper;
    private final MdParentChunkMapper parentChunkMapper;
    private final MdDocumentMapper documentMapper;

    @Value("${enterprise.kb.search.md-parent-expansion.max-chars-per-parent:2000}")
    private int maxCharsPerParent;

    @Value("${enterprise.kb.search.md-parent-expansion.max-parents:5}")
    private int maxParents;

    @Value("${enterprise.kb.search.md-parent-expansion.max-windows-per-parent:3}")
    private int maxWindowsPerParent;

    /**
     * 将 child 命中展开为完整 parent section（含多窗节选）。
     *
     * @param childHits 已 rerank 的 child 命中列表
     * @return parent 级上下文
     */
    @Override
    public List<SearchHit> expand(List<SearchHit> childHits) {
        // 按 parentId 分组，保留首次出现顺序（= parent 的 rerank 名次），并收集该 parent 全部命中 seq
        Map<UUID, ParentGroup> groups = new LinkedHashMap<>();
        for (SearchHit childHit : childHits) {
            if (childHit.chunkId() == null) {
                continue;
            }
            MdChildChunk child = childChunkMapper.findById(UUID.fromString(childHit.chunkId()));
            if (child == null) {
                continue;
            }
            ParentGroup group = groups.computeIfAbsent(child.getParentId(), k -> new ParentGroup(childHit));
            group.hitSeqs.add(child.getSeqInParent());
        }

        List<SearchHit> expanded = new ArrayList<>();
        for (Map.Entry<UUID, ParentGroup> entry : groups.entrySet()) {
            if (expanded.size() >= maxParents) {
                break;
            }
            MdParentChunk parent = parentChunkMapper.findById(entry.getKey());
            if (parent == null) {
                continue;
            }
            expanded.add(toParentHit(entry.getValue().representative, parent, entry.getValue().hitSeqs));
        }
        return expanded;
    }

    private SearchHit toParentHit(SearchHit childHit, MdParentChunk parent, Set<Integer> hitSeqs) {
        // 获取title
        String title = documentMapper.findByIdAndDeletedAtIsNull(parent.getDocumentId())
                .map(d -> d.getTitle()).orElse(childHit.documentTitle());
        return new SearchHit(
                parent.getId().toString(),
                parent.getDocumentId(),
                title,
                expandContent(parent, hitSeqs),
                null,
                childHit.score(),
                "text/markdown",
                "MD_PARENT",
                null,
                parent.getSection(),
                null);
    }

    // 开天窗
    private String expandContent(MdParentChunk parent, Set<Integer> hitSeqs) {
        String content = parent.getContent() == null ? "" : parent.getContent();
        String section = parent.getSection() == null || parent.getSection().isBlank()
                ? "" : "【" + parent.getSection() + "】\n";
        int budget = Math.max(1, maxCharsPerParent - section.length());

        if (content.length() <= budget) {
            return section + content;
        }

        List<MdChildChunk> siblings = childChunkMapper.findByParentId(parent.getId());
        if (siblings.isEmpty()) {
            return section + truncate(content, budget);
        }

        List<Integer> seqs = hitSeqs.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (seqs.isEmpty()) {
            seqs = List.of(siblings.getFirst().getSeqInParent());
        }
        List<List<Integer>> clusters = clusterAdjacent(seqs);
        if (clusters.size() > maxWindowsPerParent) {
            clusters = clusters.subList(0, maxWindowsPerParent);
        }
        int perClusterBudget = Math.max(400, budget / clusters.size());

        Map<Integer, MdChildChunk> bySeq = new LinkedHashMap<>();
        for (MdChildChunk child : siblings) {
            bySeq.put(child.getSeqInParent(), child);
        }
        // 用 TreeMap 按 seq 排序去重，跨簇命中拼接后由 renderWindow 在 seq 不连续处插省略号
        Map<Integer, MdChildChunk> selected = new TreeMap<>();
        for (List<Integer> cluster : clusters) {
            for (MdChildChunk child : buildClusterWindow(siblings, bySeq, cluster, perClusterBudget)) {
                selected.put(child.getSeqInParent(), child);
            }
        }
        String excerpt = renderWindow(parent, new ArrayList<>(selected.values()));
        return section + truncate(excerpt, budget);
    }

    /**
     * 将有序 seq 按邻近度分簇：相邻或仅隔 1 的归为同一簇。
     */
    private List<List<Integer>> clusterAdjacent(List<Integer> sortedSeqs) {
        List<List<Integer>> clusters = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        for (int seq : sortedSeqs) {
            if (current.isEmpty() || seq - current.getLast() <= 1) {
                current.add(seq);
            } else {
                clusters.add(current);
                current = new ArrayList<>();
                current.add(seq);
            }
        }
        if (!current.isEmpty()) {
            clusters.add(current);
        }
        return clusters;
    }

    /**
     * 围绕一个命中簇，按 seq 向两侧扩张到子预算为止。
     */
    private List<MdChildChunk> buildClusterWindow(List<MdChildChunk> siblings, Map<Integer, MdChildChunk> bySeq,
                                                  List<Integer> cluster, int budget) {
        int minSeq = siblings.getFirst().getSeqInParent();
        int maxSeq = siblings.getLast().getSeqInParent();
        int lo = cluster.getFirst();
        int hi = cluster.getLast();
        List<MdChildChunk> selected = new ArrayList<>();
        int total = 0;
        for (int s = lo; s <= hi; s++) {
            MdChildChunk child = bySeq.get(s);
            if (child != null) {
                selected.add(child);
                total += length(child);
            }
        }
        int left = lo - 1;
        int right = hi + 1;
        while (total < budget && (left >= minSeq || right <= maxSeq)) {
            if (left >= minSeq) {
                MdChildChunk child = bySeq.get(left--);
                if (child != null) {
                    selected.add(child);
                    total += length(child);
                }
            }
            if (total >= budget) {
                break;
            }
            if (right <= maxSeq) {
                MdChildChunk child = bySeq.get(right++);
                if (child != null) {
                    selected.add(child);
                    total += length(child);
                }
            }
        }
        return selected;
    }

    private String renderWindow(MdParentChunk parent, List<MdChildChunk> window) {
        StringBuilder sb = new StringBuilder();
        int lastSeq = -1;
        for (MdChildChunk child : window) {
            if (lastSeq >= 0 && child.getSeqInParent() > lastSeq + 1) {
                sb.append("\n\n......（中间略）......\n\n");
            }
            sb.append(extractOriginal(parent.getContent(), child));
            sb.append("\n\n");
            lastSeq = child.getSeqInParent();
        }
        return sb.toString().strip();
    }

    private String extractOriginal(String content, MdChildChunk child) {
        int start = Math.max(0, Math.min(content.length(), child.getCharStart()));
        int end = Math.max(start, Math.min(content.length(), child.getCharEnd()));
        String raw = content.substring(start, end).strip();
        if (looksLikeTableFragment(raw)) {
            return withTableHeader(content, start, raw);
        }
        return raw;
    }

    private boolean looksLikeTableFragment(String raw) {
        return raw.lines().anyMatch(line -> line.contains("|"));
    }

    private String withTableHeader(String content, int start, String raw) {
        int tableStart = content.lastIndexOf('\n', Math.max(0, start - 1));
        while (tableStart > 0) {
            int previous = content.lastIndexOf('\n', tableStart - 1);
            String line = content.substring(previous + 1, tableStart).strip();
            if (!line.contains("|")) {
                break;
            }
            tableStart = previous;
        }
        String prefix = content.substring(Math.max(0, tableStart)).lines()
                .filter(line -> line.contains("|"))
                .limit(2)
                .reduce("", (a, b) -> a + (a.isBlank() ? "" : "\n") + b);
        if (prefix.isBlank() || raw.startsWith(prefix.lines().findFirst().orElse(""))) {
            return raw;
        }
        return prefix + "\n" + raw;
    }

    private int length(MdChildChunk child) {
        return Math.max(1, child.getCharEnd() - child.getCharStart());
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(0, maxLen)) + "\n\n[section 内容过长，已截断]";
    }

    /**
     * 单次展开中，一个 parent 的代表命中（最高 rerank 名次）与其全部命中 seq。
     */
    private static final class ParentGroup {
        private final SearchHit representative;
        private final Set<Integer> hitSeqs = new TreeSet<>();

        private ParentGroup(SearchHit representative) {
            this.representative = representative;
        }
    }
}

package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.dto.SearchHit;
import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.search.dto.SearchResponse;
import com.enterprise.kb.search.service.KeywordSearchService;
import com.enterprise.kb.search.service.SemanticSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridSearchServiceImplTest {

    @Mock SemanticSearchService semanticSearchService;
    @Mock KeywordSearchService keywordSearchService;
    @InjectMocks HybridSearchServiceImpl hybridSearchService;

    private static final UUID SPACE_ID = UUID.randomUUID();
    private static final SearchRequest REQUEST = new SearchRequest("query", 10, null, null);

    // -- helpers --

    private SearchHit hit(String chunkId, String excerpt) {
        return new SearchHit(chunkId, UUID.randomUUID(), "Doc", excerpt, 1, 0.9, null);
    }

    private SearchHit hit(String chunkId, UUID docId, String excerpt) {
        return new SearchHit(chunkId, docId, "Doc", excerpt, 1, 0.9, null);
    }

    private SearchResponse response(SearchHit... hits) {
        return new SearchResponse(List.of(hits), hits.length, "TEST", 0);
    }

    // -- tracer bullet --

    @Test
    void itemInBothListsRanksAboveItemInOnlyOneList() {
        SearchHit inBoth    = hit("chunk-both", "both list excerpt");
        SearchHit onlyInSemantic = hit("chunk-semantic", "semantic only excerpt");

        // inBoth appears rank-0 in semantic and rank-0 in keyword → double RRF contribution
        // onlyInSemantic appears rank-1 in semantic only
        when(semanticSearchService.search(any(), any()))
                .thenReturn(response(inBoth, onlyInSemantic));
        when(keywordSearchService.search(any(), any()))
                .thenReturn(response(inBoth));

        List<SearchHit> hits = hybridSearchService.search(SPACE_ID, REQUEST).hits();

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).chunkId()).isEqualTo("chunk-both");
    }

    // -- behavior 2: ordering by RRF score --

    @Test
    void resultsAreOrderedByDescendingRrfScore() {
        // rank-0 item scores 1/(60+1); rank-2 item scores 1/(60+3)
        SearchHit topRank  = hit("chunk-top", "top excerpt");
        SearchHit midRank  = hit("chunk-mid", "mid excerpt");
        SearchHit lowRank  = hit("chunk-low", "low excerpt");

        when(semanticSearchService.search(any(), any()))
                .thenReturn(response(topRank, midRank, lowRank));
        when(keywordSearchService.search(any(), any()))
                .thenReturn(response());

        List<SearchHit> hits = hybridSearchService.search(SPACE_ID, REQUEST).hits();

        assertThat(hits).extracting(SearchHit::chunkId)
                .containsExactly("chunk-top", "chunk-mid", "chunk-low");
    }

    // -- behavior 3: topK is respected --

    @Test
    void topKLimitsNumberOfReturnedResults() {
        SearchHit h1 = hit("c1", "excerpt 1");
        SearchHit h2 = hit("c2", "excerpt 2");
        SearchHit h3 = hit("c3", "excerpt 3");
        SearchHit h4 = hit("c4", "excerpt 4");
        SearchHit h5 = hit("c5", "excerpt 5");

        when(semanticSearchService.search(any(), any()))
                .thenReturn(response(h1, h2, h3, h4, h5));
        when(keywordSearchService.search(any(), any()))
                .thenReturn(response());

        SearchRequest topKRequest = new SearchRequest("query", 3, null, null);
        List<SearchHit> hits = hybridSearchService.search(SPACE_ID, topKRequest).hits();

        assertThat(hits).hasSize(3);
    }

    // -- behavior 4: content deduplication --

    @Test
    void chunksWithSameDocumentAndExcerptAreDeduplicatedToOne() {
        UUID docId = UUID.randomUUID();
        String sharedExcerpt = "identical content from same document";

        // Different chunkIds but same docId + excerpt — Milvus vs PostgreSQL ID mismatch scenario
        SearchHit fromMilvus   = new SearchHit("milvus-id-123", docId, "Doc", sharedExcerpt, 1, 0.95, null);
        SearchHit fromPostgres = new SearchHit("pg-id-456",     docId, "Doc", sharedExcerpt, 1, 0.90, null);

        when(semanticSearchService.search(any(), any()))
                .thenReturn(response(fromMilvus));
        when(keywordSearchService.search(any(), any()))
                .thenReturn(response(fromPostgres));

        List<SearchHit> hits = hybridSearchService.search(SPACE_ID, REQUEST).hits();

        assertThat(hits).hasSize(1);
    }

    // -- behavior 5: empty semantic list still returns keyword results --

    @Test
    void emptySemanticResultsReturnKeywordResultsOnly() {
        SearchHit keywordOnly = hit("chunk-kw", "keyword result");

        when(semanticSearchService.search(any(), any()))
                .thenReturn(response());
        when(keywordSearchService.search(any(), any()))
                .thenReturn(response(keywordOnly));

        List<SearchHit> hits = hybridSearchService.search(SPACE_ID, REQUEST).hits();

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).chunkId()).isEqualTo("chunk-kw");
    }
}

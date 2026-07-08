package com.agent.knowledge.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.agent.common.BusinessException;
import com.agent.common.ResultCode;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing vector storage in Elasticsearch.
 * Provides index creation, document storage, and hybrid (BM25 + kNN) search
 * with Reciprocal Rank Fusion (RRF) for result combination.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchVectorStore {

    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingService embeddingService;

    /** RRF constant used in score combination */
    private static final int RRF_K = 60;

    /**
     * Creates an Elasticsearch index with the appropriate mapping if it does not already exist.
     * <p>
     * The mapping includes:
     * <ul>
     *   <li>{@code content} - text field with ik_max_word analyzer for Chinese text</li>
     *   <li>{@code vector} - dense_vector field with cosine similarity</li>
     *   <li>{@code fileName} - keyword field for filtering</li>
     *   <li>{@code knowledgeBaseId} - long field for filtering by knowledge base</li>
     * </ul>
     *
     * @param indexName the name of the index to create
     */
    public void createIndexIfNotExists(String indexName) {
        try {
            boolean indexExists = elasticsearchClient.indices()
                    .exists(e -> e.index(indexName))
                    .value();

            if (indexExists) {
                // Verify the stored vector dimension matches the current model.
                // If the dimension changed (e.g. model switched), drop and re-create.
                try {
                    var mappings = elasticsearchClient.indices()
                            .getMapping(m -> m.index(indexName))
                            .get(indexName);
                    if (mappings != null) {
                        var vectorProp = mappings.mappings().properties().get("vector");
                        if (vectorProp != null && vectorProp.isDenseVector()) {
                            Integer storedDims = vectorProp.denseVector().dims();
                            int currentDims = embeddingService.getDimension();
                            if (storedDims != null && storedDims != currentDims) {
                                log.warn("Index '{}' has dim={} but model returns dim={} – deleting and re-creating",
                                        indexName, storedDims, currentDims);
                                elasticsearchClient.indices().delete(d -> d.index(indexName));
                                // fall through to creation below
                            } else {
                                log.debug("Elasticsearch index '{}' exists with correct dim={}", indexName, storedDims);
                                return;
                            }
                        } else {
                            log.debug("Elasticsearch index already exists: {}", indexName);
                            return;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Could not verify index dimension, will skip re-creation: {}", e.getMessage());
                    return;
                }
            }

            boolean ikAvailable = isIkAnalyzerAvailable();
            String indexAnalyzer  = ikAvailable ? "ik_max_word" : "standard";
            String searchAnalyzer = ikAvailable ? "ik_smart"    : "standard";
            if (!ikAvailable) {
                log.warn("IK analyser not found – falling back to 'standard' analyser.");
            }

            int vectorDims = embeddingService.getDimension();
            CreateIndexRequest request = CreateIndexRequest.of(b -> b
                    .index(indexName)
                    .mappings(m -> m
                            .properties("content", p -> p.text(t -> t
                                    .analyzer(indexAnalyzer)
                                    .searchAnalyzer(searchAnalyzer)))
                            .properties("vector", p -> p.denseVector(d -> d
                                    .dims(vectorDims)
                                    .similarity("cosine")))
                            .properties("fileName",        p -> p.keyword(k -> k))
                            .properties("knowledgeBaseId", p -> p.long_(l -> l))
                            .properties("docId",           p -> p.keyword(k -> k))
                            .properties("chunkIndex",      p -> p.integer(i -> i))
                    ));

            CreateIndexResponse response = elasticsearchClient.indices().create(request);
            log.info("Created Elasticsearch index '{}' (dims={}, analyser={}, acknowledged={})",
                    indexName, vectorDims, indexAnalyzer, response.acknowledged());

        } catch (Exception e) {
            log.error("Failed to create Elasticsearch index: {}", indexName, e);
            throw new BusinessException(ResultCode.KB_SEARCH_ERROR,
                    "Failed to create index: " + e.getMessage());
        }
    }

    /**
     * Checks whether the IK Chinese analysis plugin is available in this
     * Elasticsearch cluster by querying the installed plugins list.
     *
     * @return {@code true} if ik_smart/ik_max_word are available
     */
    private boolean isIkAnalyzerAvailable() {
        try {
            // The _cat/plugins API is not part of the typed Java client, so we
            // use the indices.analyze endpoint: ask ES to tokenise an empty
            // string with ik_smart.  If it throws, the plugin is absent.
            elasticsearchClient.indices().analyze(a -> a
                    .analyzer("ik_smart")
                    .text("test"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Stores a document with its embedding vector and metadata in the specified index.
     *
     * @param indexName the name of the Elasticsearch index
     * @param id        the document ID
     * @param content   the text content of the document
     * @param vector    the embedding vector
     * @param metadata  additional metadata (e.g., fileName, knowledgeBaseId)
     */
    public void store(String indexName, String id, String content, float[] vector,
                      Map<String, Object> metadata) {
        try {
            Map<String, Object> document = new HashMap<>();
            document.put("content", content);
            document.put("vector", toBoxedArray(vector));
            if (metadata != null) {
                document.putAll(metadata);
            }

            elasticsearchClient.index(i -> i
                    .index(indexName)
                    .id(id)
                    .document(document));
            log.debug("Stored document {} in index {}", id, indexName);
        } catch (Exception e) {
            log.error("Failed to store document {} in index {}", id, indexName, e);
            throw new BusinessException(ResultCode.KB_SEARCH_ERROR,
                    "Failed to store document: " + e.getMessage());
        }
    }

    /**
     * Deletes all chunks belonging to a specific document from the index.
     *
     * @param indexName the name of the Elasticsearch index
     * @param docId     the document ID whose chunks should be deleted
     */
    public void deleteByDocId(String indexName, String docId) {
        try {
            elasticsearchClient.deleteByQuery(d -> d
                    .index(indexName)
                    .query(q -> q
                            .term(t -> t
                                    .field("docId")
                                    .value(FieldValue.of(docId)))));
            log.info("Deleted all chunks for docId {} from index {}", docId, indexName);
        } catch (Exception e) {
            log.error("Failed to delete chunks for docId {} from index {}", docId, indexName, e);
        }
    }

    /**
     * Performs a hybrid search combining BM25 text matching and kNN vector search.
     * Results are combined using Reciprocal Rank Fusion (RRF).
     *
     * @param indexName       the Elasticsearch index name
     * @param query           the text query for BM25 matching
     * @param queryVector     the query embedding vector for kNN search
     * @param topK            the number of top results to return
     * @param knowledgeBaseId optional filter; null means search all knowledge bases
     * @return a list of result maps containing document fields and a fused score
     */
    public List<Map<String, Object>> search(String indexName, String query,
                                            float[] queryVector, int topK,
                                            Long knowledgeBaseId) {
        try {
            List<SearchHit> textHits   = bm25Search(indexName, query, topK, knowledgeBaseId);
            log.debug("BM25 search returned {} hits (kbId={})", textHits.size(), knowledgeBaseId);

            List<SearchHit> vectorHits = knnSearch(indexName, queryVector, topK, knowledgeBaseId);
            log.debug("kNN search returned {} hits (kbId={})", vectorHits.size(), knowledgeBaseId);

            List<Map<String, Object>> fusedResults = fuseWithRRF(textHits, vectorHits, topK);
            log.debug("RRF fusion produced {} results", fusedResults.size());

            return fusedResults;
        } catch (Exception e) {
            log.error("Hybrid search failed for index {}", indexName, e);
            throw new BusinessException(ResultCode.KB_SEARCH_ERROR,
                    "Search failed: " + e.getMessage());
        }
    }

    /**
     * Backward-compatible overload that searches across all knowledge bases.
     */
    public List<Map<String, Object>> search(String indexName, String query,
                                            float[] queryVector, int topK) {
        return search(indexName, query, queryVector, topK, null);
    }

    private List<SearchHit> bm25Search(String indexName, String query, int topK,
                                       Long knowledgeBaseId) throws Exception {
        SearchResponse<Map> response = elasticsearchClient.search(s -> {
            var builder = s.index(indexName)
                    .source(src -> src.fetch(true))
                    .size(topK);
            if (knowledgeBaseId != null) {
                // Filter by kbId AND match content
                builder.query(q -> q.bool(b -> b
                        .must(m -> m.match(mm -> mm.field("content").query(FieldValue.of(query))))
                        .filter(f -> f.term(t -> t.field("knowledgeBaseId")
                                .value(FieldValue.of(knowledgeBaseId))))));
            } else {
                builder.query(q -> q.match(m -> m.field("content").query(FieldValue.of(query))));
            }
            return builder;
        }, Map.class);

        List<SearchHit> hits = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            if (hit.source() == null) {
                log.warn("BM25 hit '{}' returned null _source — RAG content will be missing. "
                        + "Check Elasticsearch _source configuration.", hit.id());
            }
            hits.add(new SearchHit(hit.id(),
                    hit.score() != null ? hit.score().doubleValue() : 0.0,
                    hit.source() != null ? hit.source() : new HashMap<>()));
        }
        return hits;
    }

    private List<SearchHit> knnSearch(String indexName, float[] queryVector, int topK,
                                      Long knowledgeBaseId) throws Exception {
        List<Float> qv = new ArrayList<>();
        for (float v : queryVector) qv.add(v);

        SearchResponse<Map> response = elasticsearchClient.search(s -> {
            var builder = s.index(indexName)
                    .source(src -> src.fetch(true))
                    .size(topK)
                    .knn(k -> {
                        var kb = k.field("vector")
                                .queryVector(qv)
                                .numCandidates(Math.max(topK * 5, 50))
                                .k(topK);
                        if (knowledgeBaseId != null) {
                            kb.filter(f -> f.term(t -> t.field("knowledgeBaseId")
                                    .value(FieldValue.of(knowledgeBaseId))));
                        }
                        return kb;
                    });
            return builder;
        }, Map.class);

        List<SearchHit> hits = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            if (hit.source() == null) {
                log.warn("kNN hit '{}' returned null _source — RAG content will be missing. "
                        + "Check Elasticsearch _source configuration.", hit.id());
            }
            hits.add(new SearchHit(hit.id(),
                    hit.score() != null ? hit.score().doubleValue() : 0.0,
                    hit.source() != null ? hit.source() : new HashMap<>()));
        }
        return hits;
    }

    /**
     * Combines two ranked lists using Reciprocal Rank Fusion.
     * <p>
     * RRF score = sum(1 / (k + rank_i)) for each list
     *
     * @param list1 the first ranked list
     * @param list2 the second ranked list
     * @param topK  the number of top results to return
     * @return the fused and re-ranked results
     */
    private List<Map<String, Object>> fuseWithRRF(List<SearchHit> list1,
                                                  List<SearchHit> list2, int topK) {
        Map<String, RrfEntry> fused = new HashMap<>();

        // Add scores from list 1
        for (int i = 0; i < list1.size(); i++) {
            SearchHit hit = list1.get(i);
            double rrfScore = 1.0 / (RRF_K + i + 1);
            fused.computeIfAbsent(hit.id(), k -> new RrfEntry(hit.source()))
                    .addScore(rrfScore);
        }

        // Add scores from list 2
        for (int i = 0; i < list2.size(); i++) {
            SearchHit hit = list2.get(i);
            double rrfScore = 1.0 / (RRF_K + i + 1);
            fused.computeIfAbsent(hit.id(), k -> new RrfEntry(hit.source()))
                    .addScore(rrfScore);
        }

        // Sort by fused score descending and return top K
        return fused.values().stream()
                .sorted((a, b) -> Double.compare(b.fusedScore, a.fusedScore))
                .limit(topK)
                .map(entry -> {
                    Map<String, Object> result = new HashMap<>(entry.source);
                    result.put("score", entry.fusedScore);
                    return result;
                })
                .toList();
    }

    /**
     * Converts a primitive float array to a boxed Float array for JSON serialization.
     *
     * @param vector the primitive float array
     * @return a boxed Float array
     */
    private Float[] toBoxedArray(float[] vector) {
        Float[] boxed = new Float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            boxed[i] = vector[i];
        }
        return boxed;
    }

    /**
     * Internal class representing a single search hit.
     */
    private record SearchHit(String id, double score, Map<String, Object> source) {
    }

    /**
     * Internal class for tracking RRF fused scores per document.
     */
    private static class RrfEntry {
        final Map<String, Object> source;
        double fusedScore = 0.0;

        RrfEntry(Map<String, Object> source) {
            this.source = source;
        }

        void addScore(double score) {
            this.fusedScore += score;
        }
    }
}

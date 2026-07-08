package com.agent.knowledge.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.agent.common.BusinessException;
import com.agent.common.ResultCode;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for generating text embeddings using the LangChain4j OpenAI embedding model.
 * <p>
 * If the API key is not configured or the API call fails, a fallback dummy vector
 * of dimension 768 is returned to allow the system to function in development mode.
 */
@Slf4j
@Service
public class EmbeddingService {

    @Value("${langchain4j.open-ai.api-key:}")
    private String apiKey;

    @Value("${langchain4j.open-ai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.embedding-model-name}")
    private String embeddingModelName;

    /** Dimension of the embedding vectors — resolved at startup by probing the real model */
    private int embeddingDimension = 1536; // default for text-embedding-v3; overridden in init()

    /**
     * Maximum number of texts per embedding API request.
     * Alibaba Cloud text-embedding-v3 rejects batches larger than 10.
     */
    private static final int BATCH_SIZE = 10;

    /** The underlying LangChain4j embedding model */
    private EmbeddingModel embeddingModel;

    /** Flag indicating whether the real model is available */
    private boolean modelAvailable = false;

    /**
     * Initializes the embedding model and detects the real vector dimension
     * by embedding a short probe string once at startup.
     */
    @PostConstruct
    public void init() {
        if (apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("your-") && !apiKey.equals("sk-placeholder")) {
            try {
                embeddingModel = OpenAiEmbeddingModel.builder()
                        .apiKey(apiKey)
                        .baseUrl(baseUrl)
                        .modelName(embeddingModelName)
                        .build();
                modelAvailable = true;

                // Probe the real dimension so ElasticsearchVectorStore creates
                // the index with the correct dense_vector dims value.
                try {
                    Response<Embedding> probe = embeddingModel.embed("probe");
                    embeddingDimension = probe.content().vector().length;
                    log.info("Embedding model '{}' initialised – dimension = {}",
                            embeddingModelName, embeddingDimension);
                } catch (Exception e) {
                    log.warn("Could not probe embedding dimension, defaulting to {}: {}",
                            embeddingDimension, e.getMessage());
                }
            } catch (Exception e) {
                log.warn("Failed to initialise embedding model, using fallback: {}", e.getMessage());
                modelAvailable = false;
            }
        } else {
            log.warn("OpenAI API key not configured – embedding service will use dummy vectors");
            modelAvailable = false;
        }
    }

    /**
     * Generates an embedding vector for the given text.
     * Falls back to a dummy vector if the model is unavailable.
     *
     * @param text the text to embed
     * @return a float array representing the embedding vector
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return dummyVector();
        }

        if (modelAvailable && embeddingModel != null) {
            try {
                Response<Embedding> response = embeddingModel.embed(text);
                return response.content().vector();
            } catch (Exception e) {
                log.warn("Embedding API call failed, using dummy vector: {}", e.getMessage());
            }
        }

        return dummyVector();
    }

    /**
     * Generates embedding vectors for a batch of texts.
     * <p>
     * Alibaba Cloud text-embedding-v3 (and many other providers) limit a single
     * request to at most 10 items.  We therefore split the input into sub-batches
     * of at most {@value #BATCH_SIZE} texts and concatenate the results.
     *
     * @param texts the list of texts to embed
     * @return a list of float arrays representing the embedding vectors
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<float[]> results = new ArrayList<>(texts.size());

        if (modelAvailable && embeddingModel != null) {
            try {
                // Split into sub-batches to respect the provider's per-request limit
                for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
                    int end = Math.min(start + BATCH_SIZE, texts.size());
                    List<String> subList = texts.subList(start, end);

                    List<TextSegment> segments = new ArrayList<>(subList.size());
                    for (String text : subList) {
                        segments.add(TextSegment.from(text));
                    }

                    Response<List<Embedding>> response = embeddingModel.embedAll(segments);
                    for (Embedding embedding : response.content()) {
                        results.add(embedding.vector());
                    }

                    log.debug("Embedded sub-batch [{}, {}) of {}", start, end, texts.size());
                }
                return results;
            } catch (Exception e) {
                log.warn("Batch embedding API call failed, using dummy vectors: {}", e.getMessage());
                results.clear(); // fall through to dummy path
            }
        }

        // Fallback: generate dummy vectors for every text
        for (int i = 0; i < texts.size(); i++) {
            results.add(dummyVector());
        }
        return results;
    }

    /**
     * Returns the actual dimension of the embedding vectors.
     * Detected at startup by probing the model; falls back to 1536 if unavailable.
     */
    public int getDimension() {
        return embeddingDimension;
    }

    /**
     * Creates a dummy vector of the detected dimension for fallback purposes.
     */
    private float[] dummyVector() {
        float[] vector = new float[embeddingDimension];
        for (int i = 0; i < embeddingDimension; i++) {
            vector[i] = (float) (Math.sin(i) * 0.001);
        }
        return vector;
    }
}

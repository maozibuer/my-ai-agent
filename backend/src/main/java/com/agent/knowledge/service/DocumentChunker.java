package com.agent.knowledge.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Service for splitting text into overlapping chunks for embedding and retrieval.
 * Chunks are first split by paragraphs, then by sentences if a paragraph exceeds
 * the chunk size, and finally assembled into overlapping windows.
 */
@Service
public class DocumentChunker {

    /** Default chunk size in characters */
    private static final int DEFAULT_CHUNK_SIZE = 500;

    /** Default overlap between adjacent chunks in characters */
    private static final int DEFAULT_OVERLAP = 100;

    /**
     * Splits text into overlapping chunks using default parameters.
     *
     * @param text the text to chunk
     * @return a list of text chunks
     */
    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * Splits text into overlapping chunks with the specified chunk size and overlap.
     * <p>
     * The algorithm works in three stages:
     * <ol>
     *   <li>Split text into paragraphs (delimited by double newlines)</li>
     *   <li>If a paragraph exceeds the chunk size, further split it by sentences</li>
     *   <li>Assemble segments into overlapping windows of approximately chunkSize characters</li>
     * </ol>
     *
     * @param text       the text to chunk
     * @param chunkSize  the target size of each chunk in characters
     * @param overlap    the overlap between adjacent chunks in characters
     * @return a list of text chunks
     */
    public List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // Stage 1: Split by paragraphs (double newline)
        List<String> paragraphs = splitByParagraphs(text);

        // Stage 2: Further split long paragraphs by sentences
        List<String> segments = new ArrayList<>();
        for (String paragraph : paragraphs) {
            if (paragraph.length() <= chunkSize) {
                segments.add(paragraph);
            } else {
                segments.addAll(splitBySentences(paragraph, chunkSize));
            }
        }

        // Stage 3: Assemble overlapping windows
        return createOverlappingWindows(segments, chunkSize, overlap);
    }

    /**
     * Splits text into paragraphs using double newlines as delimiters.
     *
     * @param text the text to split
     * @return a list of paragraphs
     */
    private List<String> splitByParagraphs(String text) {
        String[] parts = text.split("\\n\\s*\\n");
        List<String> paragraphs = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

    /**
     * Splits a long paragraph into smaller segments by sentence boundaries.
     *
     * @param paragraph the paragraph to split
     * @param chunkSize the target chunk size
     * @return a list of sentence-based segments
     */
    private List<String> splitBySentences(String paragraph, int chunkSize) {
        // Split by common sentence-ending punctuation followed by whitespace
        String[] sentences = paragraph.split("(?<=[.!?。！？])\\s+");
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            if (current.length() + sentence.length() > chunkSize && current.length() > 0) {
                segments.add(current.toString().trim());
                current = new StringBuilder();
            }
            // If a single sentence exceeds chunkSize, hard-split it
            if (sentence.length() > chunkSize) {
                if (current.length() > 0) {
                    segments.add(current.toString().trim());
                    current = new StringBuilder();
                }
                for (int i = 0; i < sentence.length(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, sentence.length());
                    segments.add(sentence.substring(i, end).trim());
                }
            } else {
                current.append(sentence).append(" ");
            }
        }

        if (current.length() > 0) {
            segments.add(current.toString().trim());
        }
        return segments;
    }


    /**
     * Assembles segments into overlapping windows of approximately chunkSize characters.
     *
     * @param segments  the input text segments (non-null, can be empty)
     * @param chunkSize the target chunk size (must be > 0)
     * @param overlap   the overlap between adjacent chunks (must be >= 0 and < chunkSize)
     * @return a list of overlapping text chunks, never null
     * @throws IllegalArgumentException if overlap >= chunkSize or chunkSize <= 0
     */
    private List<String> createOverlappingWindows(List<String> segments, int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be between 0 and chunkSize-1");
        }

        List<String> chunks = new ArrayList<>();
        if (segments == null || segments.isEmpty()) {
            return chunks;
        }

        StringBuilder window = new StringBuilder();

        for (String segment : segments) {
            // 忽略空段（可根据需要调整）
            if (segment == null || segment.isEmpty()) {
                continue;
            }

            // 处理单个段过长的情况：直接成块，清空当前窗口（保留已有chunks）
            if (segment.length() >= chunkSize) {
                if (window.length() > 0) {
                    chunks.add(window.toString().trim());
                    window.setLength(0);
                }
                chunks.add(segment);
                continue;
            }

            // 计算加入当前段后的新长度（包括可能添加的空格）
            int addedLength = window.length() + (window.length() > 0 ? 1 : 0) + segment.length();

            // 如果加入后会超出，则先保存当前窗口，并保留尾部 overlap 个字符作为新窗口起始
            if (window.length() > 0 && addedLength > chunkSize) {
                // 1. 保存当前窗口
                String chunk = window.toString().trim();
                chunks.add(chunk);

                // 2. 从 chunk 末尾截取 overlap 个字符（若不足则取全部）
                int overlapStart = Math.max(0, chunk.length() - overlap);
                String overlapText = chunk.substring(overlapStart).trim();

                // 3. 重置窗口，用重叠文本开头
                window.setLength(0);
                window.append(overlapText);

                // 4. 添加当前段
                if (window.length() > 0) {
                    window.append(" ");
                }
                window.append(segment);
            } else {
                // 未超出：直接追加
                if (window.length() > 0) {
                    window.append(" ");
                }
                window.append(segment);
            }
        }

        // 处理最后的剩余窗口
        if (window.length() > 0) {
            chunks.add(window.toString().trim());
        }

        return chunks;
    }
}

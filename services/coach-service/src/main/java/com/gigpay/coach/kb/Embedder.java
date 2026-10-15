package com.gigpay.coach.kb;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.springframework.stereotype.Component;

/**
 * Sentence embeddings computed in-process (all-MiniLM-L6-v2 on ONNX Runtime, 384 dimensions).
 * No external embedding API means no per-query cost, no data leaving the bank, and retrieval
 * that works even when the chat model is unavailable.
 */
@Component
public class Embedder {

    public static final String MODEL_NAME = "all-MiniLM-L6-v2 (ONNX, in-process)";
    private final EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel();

    public float[] embed(String text) {
        return model.embed(text).content().vector();
    }

    public static String toPgVector(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 10).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}

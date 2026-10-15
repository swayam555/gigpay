package com.gigpay.coach.kb;

/**
 * A retrieved knowledge-base chunk, with the ranks it achieved in each retriever so the UI can
 * show why it was chosen.
 */
public record Passage(long id, String docId, String docTitle, String section, String content, String source,
                      Integer denseRank, Integer lexicalRank, double rrfScore) {
}

package com.gigpay.coach.kb;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Hybrid retrieval in a single SQL statement: dense (pgvector cosine, HNSW index) and lexical
 * (Postgres full-text, GIN index) rankings fused with Reciprocal Rank Fusion, k = 60.
 *
 * <p>Dense search understands paraphrase ("someone forwarding stolen money") while lexical
 * search nails exact terms the embedding model has never seen ("STR", "FIU-IND", "1930").
 * RRF combines them by rank alone, so the two incomparable score scales never need tuning.
 */
@Component
public class HybridRetriever {

    static final int RRF_K = 60;
    private static final int CANDIDATES = 20;

    private final JdbcClient jdbc;
    private final Embedder embedder;

    public HybridRetriever(JdbcClient jdbc, Embedder embedder) {
        this.jdbc = jdbc;
        this.embedder = embedder;
    }

    public List<Passage> search(String query, int k) {
        String vector = Embedder.toPgVector(embedder.embed(query));
        return jdbc.sql("""
                        WITH q AS (
                            SELECT CAST(:vec AS vector) AS v,
                                   replace(plainto_tsquery('english', :text)::text, ' & ', ' | ') AS tq
                        ),
                        dense AS (
                            SELECT c.id, row_number() OVER (ORDER BY c.embedding <=> q.v) AS r
                            FROM kb_chunks c, q
                            ORDER BY c.embedding <=> q.v
                            LIMIT :candidates
                        ),
                        lexical AS (
                            SELECT c.id, row_number() OVER (ORDER BY ts_rank_cd(c.tsv, to_tsquery('english', q.tq)) DESC) AS r
                            FROM kb_chunks c, q
                            WHERE q.tq <> '' AND c.tsv @@ to_tsquery('english', q.tq)
                            ORDER BY ts_rank_cd(c.tsv, to_tsquery('english', q.tq)) DESC
                            LIMIT :candidates
                        )
                        SELECT c.id, c.doc_id, c.doc_title, c.section, c.content, c.source,
                               CAST(d.r AS INTEGER) AS dense_rank, CAST(l.r AS INTEGER) AS lexical_rank,
                               COALESCE(1.0 / (:rrfK + d.r), 0) + COALESCE(1.0 / (:rrfK + l.r), 0) AS rrf_score
                        FROM kb_chunks c
                        LEFT JOIN dense d ON d.id = c.id
                        LEFT JOIN lexical l ON l.id = c.id
                        WHERE d.id IS NOT NULL OR l.id IS NOT NULL
                        ORDER BY rrf_score DESC
                        LIMIT :k""")
                .params(Map.of("vec", vector, "text", query, "candidates", CANDIDATES, "rrfK", RRF_K, "k", k))
                .query(Passage.class)
                .list();
    }

    public List<Passage> byDocument(String docId) {
        return jdbc.sql("""
                        SELECT id, doc_id, doc_title, section, content, source,
                               CAST(NULL AS INTEGER) AS dense_rank, CAST(NULL AS INTEGER) AS lexical_rank,
                               0.0 AS rrf_score
                        FROM kb_chunks WHERE doc_id = ? ORDER BY id""")
                .param(docId).query(Passage.class).list();
    }

    public List<Map<String, Object>> documents() {
        return jdbc.sql("""
                        SELECT doc_id, doc_title, source, count(*) AS chunks
                        FROM kb_chunks GROUP BY doc_id, doc_title, source ORDER BY doc_id""")
                .query().listOfRows();
    }

    public long chunkCount() {
        return jdbc.sql("SELECT count(*) FROM kb_chunks").query(Long.class).single();
    }
}

CREATE EXTENSION IF NOT EXISTS vector;

-- Knowledge base for retrieval-augmented generation (hybrid: pgvector + full-text, fused with RRF).
CREATE TABLE kb_chunks (
    id           BIGSERIAL PRIMARY KEY,
    doc_id       VARCHAR(80) NOT NULL,
    doc_title    VARCHAR(200) NOT NULL,
    section      VARCHAR(200) NOT NULL,
    content      TEXT NOT NULL,
    source       VARCHAR(300),
    content_hash VARCHAR(64) NOT NULL,
    embedding    vector(384) NOT NULL,
    tsv          tsvector GENERATED ALWAYS AS (
                     setweight(to_tsvector('english', coalesce(section, '')), 'A') ||
                     setweight(to_tsvector('english', content), 'B')) STORED
);
CREATE INDEX idx_kb_embedding ON kb_chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_kb_tsv ON kb_chunks USING gin (tsv);
CREATE INDEX idx_kb_doc ON kb_chunks (doc_id);

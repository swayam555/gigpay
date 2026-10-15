package com.gigpay.coach.kb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Loads the Markdown knowledge base, splits it into section-sized chunks and stores each chunk
 * with its embedding. Re-ingestion is idempotent: a document is only re-embedded when one of
 * its chunks has changed.
 */
@Component
public class KnowledgeBaseIngestor {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseIngestor.class);
    static final int MAX_CHUNK_CHARS = 1400;

    private final JdbcClient jdbc;
    private final Embedder embedder;
    private final TransactionTemplate tx;

    public KnowledgeBaseIngestor(JdbcClient jdbc, Embedder embedder, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.embedder = embedder;
        this.tx = tx;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ingestOnStartup() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:kb/*.md");
        int changed = 0;
        for (Resource file : files) {
            String docId = file.getFilename().replace(".md", "");
            if (ingest(docId, file.getContentAsString(StandardCharsets.UTF_8))) changed++;
        }
        log.info("Knowledge base ready: {} documents, {} re-embedded", files.length, changed);
    }

    boolean ingest(String docId, String markdown) {
        Document doc = parse(markdown);
        List<Chunk> chunks = chunk(doc);
        Set<String> hashes = new HashSet<>();
        chunks.forEach(c -> hashes.add(c.hash()));
        Set<String> existing = new HashSet<>(jdbc.sql("SELECT content_hash FROM kb_chunks WHERE doc_id = ?")
                .param(docId).query(String.class).list());
        if (existing.equals(hashes)) return false;

        List<float[]> vectors = new ArrayList<>();
        for (Chunk c : chunks) vectors.add(embedder.embed(doc.title() + " - " + c.section() + "\n" + c.content()));
        tx.executeWithoutResult(s -> {
            jdbc.sql("DELETE FROM kb_chunks WHERE doc_id = ?").param(docId).update();
            for (int i = 0; i < chunks.size(); i++) {
                Chunk c = chunks.get(i);
                jdbc.sql("""
                                INSERT INTO kb_chunks (doc_id, doc_title, section, content, source, content_hash, embedding)
                                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS vector))""")
                        .params(docId, doc.title(), c.section(), c.content(), doc.source(), c.hash(),
                                Embedder.toPgVector(vectors.get(i)))
                        .update();
            }
        });
        return true;
    }

    static Document parse(String markdown) {
        String title = "Untitled";
        String source = null;
        String body = markdown;
        if (markdown.startsWith("---")) {
            int end = markdown.indexOf("\n---", 3);
            for (String line : markdown.substring(3, end).split("\n")) {
                if (line.startsWith("title:")) title = line.substring(6).trim();
                if (line.startsWith("source:")) source = line.substring(7).trim();
            }
            body = markdown.substring(end + 4);
        }
        return new Document(title, source, body.trim());
    }

    /** One chunk per "## " section; long sections are split on paragraph boundaries. */
    static List<Chunk> chunk(Document doc) {
        List<Chunk> out = new ArrayList<>();
        for (String block : doc.body().split("(?m)^## ")) {
            if (block.isBlank()) continue;
            int nl = block.indexOf('\n');
            String section = nl < 0 ? block.trim() : block.substring(0, nl).trim();
            String text = nl < 0 ? "" : block.substring(nl + 1).trim();
            if (text.isEmpty()) continue;
            StringBuilder current = new StringBuilder();
            for (String para : text.split("\n\\s*\n")) {
                if (current.length() > 0 && current.length() + para.length() > MAX_CHUNK_CHARS) {
                    out.add(new Chunk(section, current.toString().trim()));
                    current.setLength(0);
                }
                current.append(para).append("\n\n");
            }
            if (!current.isEmpty()) out.add(new Chunk(section, current.toString().trim()));
        }
        return out;
    }

    record Document(String title, String source, String body) {
    }

    record Chunk(String section, String content) {
        String hash() {
            try {
                byte[] d = MessageDigest.getInstance("SHA-256")
                        .digest((section + "\u0000" + content).getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(d);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}

package com.gigpay.coach.ai;

import com.gigpay.coach.kb.HybridRetriever;
import com.gigpay.coach.kb.Passage;
import com.gigpay.coach.tools.CoachTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Answers a worker's question. With a language model: retrieved knowledge goes into the prompt,
 * the model may call tools to read the worker's real data, and replies are kept in a short
 * per-worker memory so follow-ups work ("and last week?"). Without one, the rule-based coach
 * answers from the same data and knowledge.
 */
@Service
public class CoachService {

    private static final Logger log = LoggerFactory.getLogger(CoachService.class);

    private final HybridRetriever retriever;
    private final FallbackCoach fallback;
    private final CoachTools tools;
    private final ChatModels models;
    private final CoachAssistant assistant;

    public CoachService(HybridRetriever retriever, FallbackCoach fallback, CoachTools tools, ChatModels models) {
        this.retriever = retriever;
        this.fallback = fallback;
        this.tools = tools;
        this.models = models;
        this.assistant = models.model().map(m -> AiServices.builder(CoachAssistant.class)
                .chatModel(m)
                .tools(tools)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(12))
                .build()).orElse(null);
    }

    public Answer ask(String workerId, String message) {
        if (message == null || message.isBlank()) throw new IllegalArgumentException("Ask me something");
        if (message.length() > 500) throw new IllegalArgumentException("Please keep questions under 500 characters");
        long start = System.currentTimeMillis();
        List<Passage> passages = retriever.search(message, 4);

        if (assistant != null) {
            try {
                String reply = assistant.chat(workerId, message + "\n\nKNOWLEDGE\n" + knowledgeBlock(passages));
                Set<String> used = tools.drainUsed(workerId);
                return new Answer(reply, new ArrayList<>(used), citations(passages), models.description(), true,
                        System.currentTimeMillis() - start);
            } catch (RuntimeException e) {
                tools.drainUsed(workerId);
                log.warn("LLM coach failed ({}); answering with the rule-based coach", e.getMessage());
            }
        }
        FallbackCoach.Reply r = fallback.answer(workerId, message, passages);
        boolean usedKnowledge = r.toolsUsed().isEmpty();
        return new Answer(r.text(), r.toolsUsed(), usedKnowledge ? citations(passages) : List.of(),
                "rule-based coach", false, System.currentTimeMillis() - start);
    }

    static String knowledgeBlock(List<Passage> passages) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < passages.size(); i++) {
            Passage p = passages.get(i);
            sb.append('[').append(i + 1).append("] ").append(p.docTitle()).append(" - ").append(p.section()).append('\n')
                    .append(p.content()).append("\n\n");
        }
        return sb.toString().trim();
    }

    static List<Citation> citations(List<Passage> passages) {
        List<Citation> out = new ArrayList<>();
        for (int i = 0; i < passages.size(); i++) {
            Passage p = passages.get(i);
            out.add(new Citation(i + 1, p.docTitle(), p.section(),
                    p.content().length() > 260 ? p.content().substring(0, 257) + "..." : p.content(), p.source()));
        }
        return out;
    }

    public record Citation(int n, String docTitle, String section, String snippet, String source) {
    }

    public record Answer(String answer, List<String> toolsUsed, List<Citation> citations, String generatedBy, boolean llm,
                         long latencyMs) {
    }
}

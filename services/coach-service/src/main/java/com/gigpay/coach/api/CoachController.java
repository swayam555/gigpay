package com.gigpay.coach.api;

import com.gigpay.coach.ai.ChatModels;
import com.gigpay.coach.ai.CoachService;
import com.gigpay.coach.kb.Embedder;
import com.gigpay.coach.kb.HybridRetriever;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coach")
public class CoachController {

    private final CoachService coach;
    private final ChatModels models;
    private final HybridRetriever retriever;

    public CoachController(CoachService coach, ChatModels models, HybridRetriever retriever) {
        this.coach = coach;
        this.models = models;
        this.retriever = retriever;
    }

    @PostMapping("/chat")
    public CoachService.Answer chat(@RequestHeader("X-User-Id") String workerId, @RequestBody Map<String, String> body) {
        return coach.ask(workerId, body.get("message"));
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("llmEnabled", models.model().isPresent(), "chatModel", models.description(),
                "embeddingModel", Embedder.MODEL_NAME, "knowledgeChunks", retriever.chunkCount(),
                "tools", List.of("earningsSummary", "dailyEarnings", "trips", "incentiveProgress", "wallet", "payouts"));
    }

    @GetMapping("/suggestions")
    public List<String> suggestions() {
        return List.of("How much did I earn today?", "Why are my earnings less today?", "Which trip paid the least?",
                "How much can I withdraw right now?", "My payout failed, where is my money?",
                "How close am I to today's bonus?", "Do I need to pay income tax?", "How do I register on e-Shram?",
                "Which insurance should I take for ₹20?", "Aaj kitna kamaya?");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail bad(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}

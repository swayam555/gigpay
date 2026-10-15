package com.gigpay.coach.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Functions the language model may call. Each takes the worker id from the conversation's memory
 * id (set by the server from the verified token), not from model output. Amounts come back in
 * paise; the system prompt tells the model to convert to rupees.
 */
@Component
public class CoachTools {

    private static final Set<String> PERIODS = Set.of("today", "week", "lastweek");

    private final WorkerData data;
    /** Which tools ran during the current reply, so the UI can show "looked up your wallet". */
    private final Map<String, Set<String>> used = new ConcurrentHashMap<>();

    public CoachTools(WorkerData data) {
        this.data = data;
    }

    @Tool("Earnings totals for a period: net pay, trips, incentives, tips, platform commission, minutes worked, "
            + "net pay per hour, and a per-platform split. Amounts in paise.")
    public Map<String, Object> earningsSummary(@ToolMemoryId String workerId,
                                               @P("One of: today, week (since Monday), lastweek") String period) {
        mark(workerId, "earningsSummary");
        return data.summary(workerId, PERIODS.contains(period) ? period : "today");
    }

    @Tool("Net earnings and trip count for each of the last 7 days. Amounts in paise.")
    public List<Map<String, Object>> dailyEarnings(@ToolMemoryId String workerId) {
        mark(workerId, "dailyEarnings");
        return data.daily(workerId);
    }

    @Tool("The worker's recent trips with a line-by-line pay breakdown. Use order=lowest to find the lowest-paid trips.")
    public List<Map<String, Object>> trips(@ToolMemoryId String workerId,
                                           @P("How many trips, 1-10") int limit,
                                           @P("recent or lowest") String order) {
        mark(workerId, "trips");
        return data.trips(workerId, Math.max(1, Math.min(limit, 10)), "lowest".equals(order) ? "lowest" : "recent");
    }

    @Tool("Today's progress toward each platform's daily streak bonus.")
    public List<Map<String, Object>> incentiveProgress(@ToolMemoryId String workerId) {
        mark(workerId, "incentiveProgress");
        return data.incentives(workerId);
    }

    @Tool("GigPay wallet: amount owed to the worker, how much they can withdraw instantly right now, the fee and limits. Amounts in paise.")
    public Map<String, Object> wallet(@ToolMemoryId String workerId) {
        mark(workerId, "wallet");
        return data.wallet(workerId);
    }

    @Tool("Recent payouts (instant and weekly) with their status and any failure reason. Amounts in paise.")
    public List<Map<String, Object>> payouts(@ToolMemoryId String workerId) {
        mark(workerId, "payouts");
        List<Map<String, Object>> all = data.payouts(workerId);
        return all.subList(0, Math.min(all.size(), 8));
    }

    void mark(String workerId, String tool) {
        used.computeIfAbsent(workerId, k -> ConcurrentHashMap.newKeySet()).add(tool);
    }

    public Set<String> drainUsed(String workerId) {
        Set<String> s = used.remove(workerId);
        return s == null ? Set.of() : Set.copyOf(s);
    }
}

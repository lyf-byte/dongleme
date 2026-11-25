package com.dongleme.backend.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.common.ShanghaiTime;
import com.dongleme.backend.dto.SummaryRequest;
import com.dongleme.backend.dto.SummaryView;
import com.dongleme.backend.entity.CheckinRecord;
import com.dongleme.backend.entity.WeeklySummary;
import com.dongleme.backend.mapper.CheckinRecordMapper;
import com.dongleme.backend.mapper.SummaryMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SummaryService {

    private static final int MAX_REGEN_PER_WEEK = 5;

    private static final String EMPTY_CONTENT =
            "本周还没有打卡记录。先从 10 分钟快走开始吧，动起来比动得多更重要。"
                    + "下周给自己定一个小目标：完成 3 次打卡。";

    private final SummaryMapper summaryMapper;
    private final CheckinRecordMapper recordMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final String llmUrl;
    private final String llmKey;
    private final String llmModel;

    public SummaryService(
            SummaryMapper summaryMapper,
            CheckinRecordMapper recordMapper,
            ObjectMapper objectMapper,
            @Value("${app.llm.url:}") String llmUrl,
            @Value("${app.llm.key:}") String llmKey,
            @Value("${app.llm.model:Qwen/Qwen2.5-7B-Instruct}") String llmModel) {
        this.summaryMapper = summaryMapper;
        this.recordMapper = recordMapper;
        this.objectMapper = objectMapper;
        this.llmUrl = llmUrl;
        this.llmKey = llmKey;
        this.llmModel = llmModel;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public SummaryView generate(SummaryRequest request, String userId) {
        boolean peek = request != null && Boolean.TRUE.equals(request.peek());
        boolean force = request != null && Boolean.TRUE.equals(request.force());

        LocalDate today = ShanghaiTime.today();
        LocalDate weekStart = ShanghaiTime.weekStart(today);

        WeeklySummary cached = summaryMapper.selectOne(
                Wrappers.<WeeklySummary>lambdaQuery()
                        .eq(WeeklySummary::getUserId, userId)
                        .eq(WeeklySummary::getWeekStart, weekStart)
                        .last("limit 1"));

        if (peek) {
            return new SummaryView(
                    weekStart,
                    cached == null ? null : cached.getContent(),
                    cached != null,
                    null,
                    null,
                    hasNewRecords(userId, cached),
                    cached == null ? null : planGoalOf(cached, userId, weekStart, today));
        }

        if (cached != null && !force) {
            return new SummaryView(
                    weekStart,
                    cached.getContent(),
                    true,
                    null,
                    null,
                    hasNewRecords(userId, cached),
                    planGoalOf(cached, userId, weekStart, today));
        }

        int cachedRegenCount = cached == null || cached.getRegenCount() == null
                ? 0
                : cached.getRegenCount();
        if (cached != null && force && cachedRegenCount >= MAX_REGEN_PER_WEEK) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "本周重新生成次数已用完（最多 5 次），下周一自动刷新");
        }

        List<CheckinRecord> rows = recordMapper.selectList(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(CheckinRecord::getUserId, userId)
                        .ge(CheckinRecord::getDate, weekStart)
                        .le(CheckinRecord::getDate, today));

        WeekStats stats = aggregate(rows);
        if (stats.count == 0) {
            return new SummaryView(weekStart, EMPTY_CONTENT, false, null, true, null, suggestGoal(stats));
        }

        String fallback = buildFallback(stats);
        if (llmUrl == null || llmUrl.isBlank() || llmKey == null || llmKey.isBlank()) {
            saveSummary(userId, weekStart, fallback, suggestGoal(stats), cached, force, cachedRegenCount);
            return new SummaryView(weekStart, fallback, false, true, null, null, suggestGoal(stats));
        }

        try {
            LlmResult result = callLlm(stats);
            int regenCount = cached != null && force ? cachedRegenCount + 1 : 0;

            WeeklySummary summary = new WeeklySummary();
            summary.setUserId(userId);
            summary.setWeekStart(weekStart);
            summary.setContent(result.summary());
            summary.setPlanGoal(result.planGoal());
            summary.setRegenCount(regenCount);
            summaryMapper.upsert(summary);

            return new SummaryView(
                    weekStart, result.summary(), false, null, null, null, result.planGoal());
        } catch (Exception exception) {
            saveSummary(userId, weekStart, fallback, suggestGoal(stats), cached, force, cachedRegenCount);
            return new SummaryView(weekStart, fallback, false, true, null, null, suggestGoal(stats));
        }
    }

    private Integer planGoalOf(
            WeeklySummary cached, String userId, LocalDate weekStart, LocalDate today) {
        if (cached.getPlanGoal() != null) {
            return cached.getPlanGoal();
        }
        return suggestGoal(loadStats(userId, weekStart, today));
    }

    private WeekStats loadStats(String userId, LocalDate weekStart, LocalDate today) {
        return aggregate(recordMapper.selectList(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(CheckinRecord::getUserId, userId)
                        .ge(CheckinRecord::getDate, weekStart)
                        .le(CheckinRecord::getDate, today)));
    }

    /**
     * 没有 AI 结构化结果时的目标建议：在完成量基础上增加约一成，取整到 10 分钟。
     */
    private static int suggestGoal(WeekStats stats) {
        if (stats.count == 0) {
            return 90;
        }
        int base = Math.max(stats.minutes + 30, (int) Math.round(stats.minutes * 1.1));
        int rounded = (int) Math.ceil(base / 10.0) * 10;
        return Math.max(60, Math.min(2000, rounded));
    }

    private void saveSummary(
            String userId,
            LocalDate weekStart,
            String content,
            int planGoal,
            WeeklySummary cached,
            boolean force,
            int cachedRegenCount) {
        WeeklySummary summary = new WeeklySummary();
        summary.setUserId(userId);
        summary.setWeekStart(weekStart);
        summary.setContent(content);
        summary.setPlanGoal(planGoal);
        summary.setRegenCount(cached != null && force ? cachedRegenCount + 1 : 0);
        summaryMapper.upsert(summary);
    }

    private boolean hasNewRecords(String userId, WeeklySummary cached) {
        if (cached == null || cached.getCreatedAt() == null) {
            return false;
        }
        return recordMapper.selectCount(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(CheckinRecord::getUserId, userId)
                        .gt(CheckinRecord::getCreatedAt, cached.getCreatedAt())) > 0;
    }

    private WeekStats aggregate(List<CheckinRecord> rows) {
        WeekStats stats = new WeekStats();
        for (CheckinRecord row : rows) {
            int duration = row.getDuration() == null ? 0 : row.getDuration();
            boolean flagged = row.getFlagged() != null && row.getFlagged() == 1;
            int effectiveDuration = flagged ? 0 : duration;
            stats.count += 1;
            stats.minutes += effectiveDuration;
            stats.calories += flagged || row.getCalories() == null ? 0 : row.getCalories();
            if (effectiveDuration > 0) {
                stats.types.merge(row.getType(), effectiveDuration, Integer::sum);
            }
        }
        return stats;
    }

    private String buildFallback(WeekStats stats) {
        return "本周你完成了 " + stats.count + " 次打卡，累计 " + stats.minutes
                + " 分钟，约消耗 " + stats.calories + " 千卡。"
                + (stats.minutes >= 150
                        ? "已经达到每周 150 分钟的基础运动量，节奏保持得不错。"
                        : "距离每周 150 分钟还差一点，下周可以安排两次快走或骑行补上。")
                + "下周建议固定两个时间段运动，把打卡变成习惯。";
    }

    private LlmResult callLlm(WeekStats stats) throws Exception {
        Map<String, Object> statsMap = new LinkedHashMap<>();
        statsMap.put("count", stats.count);
        statsMap.put("minutes", stats.minutes);
        statsMap.put("calories", stats.calories);
        statsMap.put("types", stats.types);

        String prompt = "你是运动教练。用户本周数据："
                + objectMapper.writeValueAsString(statsMap)
                + "。写 150 字以内中文小结，含数据回顾、亮点、不足、下周建议，语气轻松鼓励；"
                + "并给出下周建议的每周运动总分钟数（60–2000 的整数）。"
                + "只输出 JSON，不要额外文字：{\"summary\":\"小结文本\",\"planGoal\":200}";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", llmModel);
        payload.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        payload.put("temperature", 0.7);
        payload.put("max_tokens", 600);

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(llmUrl))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + llmKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(
                httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("LLM " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        String raw = contentNode.isTextual() ? contentNode.asText().trim() : "";
        if (raw.isEmpty()) {
            throw new IllegalStateException("LLM empty content");
        }

        JsonNode parsed = objectMapper.readTree(extractJson(raw));
        String summary = parsed.path("summary").asText("").trim();
        if (summary.isEmpty()) {
            throw new IllegalStateException("LLM empty summary");
        }
        int planGoal = parsed.path("planGoal").asInt(0);
        if (planGoal < 60 || planGoal > 2000) {
            planGoal = suggestGoal(stats);
        }
        return new LlmResult(summary, planGoal);
    }

    /** 兼容模型把 JSON 包在 ```json 代码块里的情况 */
    private static String extractJson(String raw) {
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            if (firstLineEnd >= 0) {
                text = text.substring(firstLineEnd + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return text;
        }
        return text.substring(start, end + 1);
    }

    private record LlmResult(String summary, int planGoal) {
    }

    private static class WeekStats {
        private int count;
        private int minutes;
        private int calories;
        private final Map<String, Integer> types = new LinkedHashMap<>();
    }
}

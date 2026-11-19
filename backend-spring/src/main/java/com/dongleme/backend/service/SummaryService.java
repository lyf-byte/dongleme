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
                    null);
        }

        if (cached != null && !force) {
            return new SummaryView(weekStart, cached.getContent(), true, null, null);
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
            return new SummaryView(weekStart, EMPTY_CONTENT, false, null, true);
        }

        String fallback = buildFallback(stats);
        if (llmUrl == null || llmUrl.isBlank() || llmKey == null || llmKey.isBlank()) {
            return new SummaryView(weekStart, fallback, false, true, null);
        }

        try {
            String content = callLlm(stats);
            int regenCount = cached != null && force ? cachedRegenCount + 1 : 0;

            WeeklySummary summary = new WeeklySummary();
            summary.setUserId(userId);
            summary.setWeekStart(weekStart);
            summary.setContent(content);
            summary.setRegenCount(regenCount);
            summaryMapper.upsert(summary);

            return new SummaryView(weekStart, content, false, null, null);
        } catch (Exception exception) {
            return new SummaryView(weekStart, fallback, false, true, null);
        }
    }

    private WeekStats aggregate(List<CheckinRecord> rows) {
        WeekStats stats = new WeekStats();
        for (CheckinRecord row : rows) {
            int duration = row.getDuration() == null ? 0 : row.getDuration();
            stats.count += 1;
            stats.minutes += duration;
            stats.calories += row.getCalories() == null ? 0 : row.getCalories();
            stats.types.merge(row.getType(), duration, Integer::sum);
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

    private String callLlm(WeekStats stats) throws Exception {
        Map<String, Object> statsMap = new LinkedHashMap<>();
        statsMap.put("count", stats.count);
        statsMap.put("minutes", stats.minutes);
        statsMap.put("calories", stats.calories);
        statsMap.put("types", stats.types);

        String prompt = "你是运动教练。用户本周数据："
                + objectMapper.writeValueAsString(statsMap)
                + "。写150字以内中文小结，含数据回顾、亮点、不足、下周建议。语气轻松鼓励。";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", llmModel);
        payload.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        payload.put("temperature", 0.7);
        payload.put("max_tokens", 400);

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
        String content = contentNode.isTextual() ? contentNode.asText().trim() : "";
        if (content.isEmpty()) {
            throw new IllegalStateException("LLM empty content");
        }
        return content;
    }

    private static class WeekStats {
        private int count;
        private int minutes;
        private int calories;
        private final Map<String, Integer> types = new LinkedHashMap<>();
    }
}

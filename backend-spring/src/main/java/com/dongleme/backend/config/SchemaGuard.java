package com.dongleme.backend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 启动时校验防刷相关字段是否已迁移。
 * 缺列时直接启动失败，避免线上打卡接口等到请求时才 500。
 */
@Component
public class SchemaGuard implements ApplicationRunner {

    private static final Map<String, List<String>> REQUIRED_COLUMNS = Map.of(
            "records", List.of("source", "verified", "flagged"),
            "summaries", List.of("plan_goal"),
            "users", List.of("next_weekly_goal", "next_goal_week")
    );

    private final JdbcTemplate jdbcTemplate;

    public SchemaGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> missing = new ArrayList<>();
        REQUIRED_COLUMNS.forEach((table, columns) -> columns.forEach(column -> {
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from information_schema.columns "
                            + "where table_schema = database() and table_name = ? and column_name = ?",
                    Integer.class, table, column);
            if (count == null || count == 0) {
                missing.add(table + "." + column);
            }
        }));
        Integer badgeTable = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema = database() and table_name = 'user_badges'",
                Integer.class);
        if (badgeTable == null || badgeTable == 0) {
            missing.add("user_badges");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("数据库结构未迁移，缺少：" + String.join("、", missing)
                    + "。请先执行 mysql/schema.sql 中的迁移语句，再启动后端。");
        }
    }
}

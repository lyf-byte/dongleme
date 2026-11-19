package com.dongleme.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public SchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from information_schema.columns "
                            + "where table_schema = database() "
                            + "and table_name = 'users' and column_name = 'gender'",
                    Integer.class);
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.execute(
                    "alter table users add column gender varchar(10) not null default 'secret' after nickname");
            log.info("users 表已新增 gender 字段");
        } catch (Exception exception) {
            log.error("自动迁移 gender 字段失败，请手动执行："
                    + "alter table users add column gender varchar(10) not null default 'secret' after nickname",
                    exception);
        }
    }
}

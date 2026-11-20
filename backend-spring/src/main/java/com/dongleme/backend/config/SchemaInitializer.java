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

    private static final String CREATE_ACTIVITY_ROUTES = """
            create table if not exists activity_routes (
              id bigint unsigned auto_increment primary key,
              user_id char(36) not null,
              date date not null,
              type varchar(20) not null,
              started_at datetime not null,
              ended_at datetime not null,
              duration_sec int not null,
              distance_m int not null,
              calories int not null,
              point_count int not null,
              points longtext not null,
              start_lat decimal(10, 7) null,
              start_lng decimal(10, 7) null,
              end_lat decimal(10, 7) null,
              end_lng decimal(10, 7) null,
              client_id char(36) not null,
              source varchar(16) not null default 'web',
              created_at datetime not null default current_timestamp,
              unique key uk_routes_user_client (user_id, client_id),
              key idx_routes_user_date (user_id, date),
              key idx_routes_user_created (user_id, created_at),
              constraint fk_routes_user foreign key (user_id)
                references users (id) on delete cascade,
              constraint chk_routes_duration check (duration_sec between 60 and 21600),
              constraint chk_routes_distance check (distance_m >= 0)
            ) engine = InnoDB default charset = utf8mb4
            """;

    private final JdbcTemplate jdbcTemplate;

    public SchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureGenderColumn();
        ensureActivityRoutesTable();
        ensureRoutePreviewColumn();
    }

    private void ensureGenderColumn() {
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

    private void ensureActivityRoutesTable() {
        try {
            jdbcTemplate.execute(CREATE_ACTIVITY_ROUTES);
            log.info("activity_routes 表已就绪");
        } catch (Exception exception) {
            log.error("自动创建 activity_routes 表失败，请手动执行 mysql/schema.sql", exception);
        }
    }

    private void ensureRoutePreviewColumn() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from information_schema.columns "
                            + "where table_schema = database() "
                            + "and table_name = 'activity_routes' and column_name = 'preview'",
                    Integer.class);
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.execute(
                    "alter table activity_routes add column preview text null after points");
            log.info("activity_routes 表已新增 preview 字段");
        } catch (Exception exception) {
            log.error("自动迁移 preview 字段失败，请手动执行："
                    + "alter table activity_routes add column preview text null after points",
                    exception);
        }
    }
}

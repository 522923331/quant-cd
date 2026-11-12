package com.quantcd.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AshareSchemaUpgrade implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public AshareSchemaUpgrade(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        addColumn("ashare_year_stat", "net_margin");
        addColumn("ashare_year_stat", "gross_margin");
        addColumn("ashare_year_stat", "roe");
        addColumn("ashare_year_stat", "ocf_to_profit");
        addColumn("ashare_indicator", "ocf_to_profit");
    }

    private void addColumn(String table, String column) {
        List<String> names = jdbc.query("PRAGMA table_info(" + table + ")",
                (rs, rowNum) -> rs.getString("name"));
        if (!names.contains(column)) {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " TEXT");
        }
    }
}

package com.quantcd.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SqliteDirectoryInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(SqliteDirectoryInitializer.class);

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        createParent(context.getEnvironment().getProperty("spring.datasource.url"));
    }

    static void createParent(String jdbcUrl) {
        Path parent = parentOf(jdbcUrl);
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
            log.info("数据库目录 {}", parent.toAbsolutePath());
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建数据库目录 " + parent, ex);
        }
    }

    static Path parentOf(String jdbcUrl) {
        String file = sqliteFile(jdbcUrl);
        if (file == null) {
            return null;
        }
        return Paths.get(file).getParent();
    }

    private static String sqliteFile(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:sqlite:")) {
            return null;
        }
        if (jdbcUrl.contains(":memory:") || jdbcUrl.contains("mode=memory")) {
            return null;
        }
        String location = jdbcUrl.substring("jdbc:sqlite:".length());
        int query = location.indexOf('?');
        if (query >= 0) {
            location = location.substring(0, query);
        }
        if (location.startsWith("file:")) {
            location = location.substring("file:".length());
        }
        return location.isEmpty() ? null : location;
    }
}

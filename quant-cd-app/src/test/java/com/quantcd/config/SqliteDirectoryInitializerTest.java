package com.quantcd.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteDirectoryInitializerTest {

    @Test
    void readsTheParentFromTheConfiguredUrl() {
        Path parent = SqliteDirectoryInitializer.parentOf(
                "jdbc:sqlite:./data/quant-cd.db?journal_mode=WAL&busy_timeout=5000");
        assertEquals(Paths.get("./data"), parent);
    }

    @Test
    void memoryAndOtherDatabasesHaveNoDirectory() {
        assertNull(SqliteDirectoryInitializer.parentOf("jdbc:sqlite::memory:"));
        assertNull(SqliteDirectoryInitializer.parentOf("jdbc:sqlite:file::memory:?cache=shared"));
        assertNull(SqliteDirectoryInitializer.parentOf(null));
        assertNull(SqliteDirectoryInitializer.parentOf("jdbc:h2:mem:test"));
    }

    @Test
    void createsOnlyTheParentDirectory(@TempDir Path root) {
        Path database = root.resolve("data").resolve("quant-cd.db");
        SqliteDirectoryInitializer.createParent("jdbc:sqlite:" + database + "?journal_mode=WAL");
        assertTrue(Files.isDirectory(database.getParent()));
        assertFalse(Files.exists(database));
    }
}

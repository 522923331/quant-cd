package com.quantcd.crypto.notify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LarkNotifierTest {

    @Test
    void signMatchesHmacOfTimestampAndSecret() {
        assertEquals("6o+SjynWLFd+QtSzfgy9uvrayMJ+/S8z4k5MmO7xW68=", LarkNotifier.sign("1", "secret"));
    }
}

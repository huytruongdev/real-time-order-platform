package com.realtimeorder.shared.id;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

import java.util.UUID;

/**
 * Sinh UUIDv7 ở application layer (ADR-027).
 *
 * UUIDv7 có 48 bit đầu là Unix timestamp (ms), nên các ID sinh sau có giá trị lớn hơn.
 * Điều này giúp B-tree index của primary key chèn gần cuối thay vì ngẫu nhiên như UUIDv4.
 */
public final class IdGenerator {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private IdGenerator() {
    }

    public static UUID newId() {
        return GENERATOR.generate();
    }
}

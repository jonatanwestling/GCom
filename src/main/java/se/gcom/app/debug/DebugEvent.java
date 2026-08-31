package se.gcom.app.debug;

import java.time.Instant;

public record DebugEvent(
        long sequenceNumber,
        Instant timestamp,
        DebugEventType type,
        String source,
        String details
) { }

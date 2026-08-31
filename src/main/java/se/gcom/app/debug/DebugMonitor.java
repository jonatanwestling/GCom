package se.gcom.app.debug;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class DebugMonitor {

    private final AtomicLong sequence = new AtomicLong();
    private final ObservableList<DebugEvent> events = FXCollections.observableArrayList();

    public ObservableList<DebugEvent> getEvents() {
        return events;
    }

    public void recordEvent(DebugEventType type, String source, String details) {
        DebugEvent event = new DebugEvent(
                sequence.incrementAndGet(),
                Instant.now(),
                type,
                source,
                details
        );

        if (Platform.isFxApplicationThread()) {
            events.add(event);
        } else {
            Platform.runLater(() -> events.add(event));
        }
    }

}

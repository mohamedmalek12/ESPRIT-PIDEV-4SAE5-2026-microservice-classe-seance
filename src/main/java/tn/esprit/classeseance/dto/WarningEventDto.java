package tn.esprit.classeseance.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pushed to /topic/warnings (WebSocket) and listed via GET /api/warnings.
 */
public class WarningEventDto {

    private String id;
    private Instant timestamp;
    private String source;
    private String severity;
    private List<String> messages;
    private Integer seanceId;

    public WarningEventDto() {
    }

    public static WarningEventDto ofSession(Integer seanceId, List<String> messages) {
        WarningEventDto e = new WarningEventDto();
        e.id = UUID.randomUUID().toString();
        e.timestamp = Instant.now();
        e.source = "SESSION";
        e.severity = "WARNING";
        e.messages = messages;
        e.seanceId = seanceId;
        return e;
    }

    /** External events (material, etc.) — no session id. */
    public static WarningEventDto ofExternal(String source, List<String> messages) {
        WarningEventDto e = new WarningEventDto();
        e.id = UUID.randomUUID().toString();
        e.timestamp = Instant.now();
        e.source = source != null && !source.isBlank() ? source : "APP";
        e.severity = "WARNING";
        e.messages = messages;
        e.seanceId = null;
        return e;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public List<String> getMessages() {
        return messages;
    }

    public void setMessages(List<String> messages) {
        this.messages = messages;
    }

    public Integer getSeanceId() {
        return seanceId;
    }

    public void setSeanceId(Integer seanceId) {
        this.seanceId = seanceId;
    }
}

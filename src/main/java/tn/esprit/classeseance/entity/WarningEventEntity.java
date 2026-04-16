package tn.esprit.classeseance.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "warning_event", indexes = @Index(name = "idx_warning_event_time", columnList = "event_time"))
public class WarningEventEntity {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_OF_STRING = new TypeReference<>() {
    };

    @Id
    @Column(length = 40, nullable = false)
    private String id;

    @Column(name = "event_time", nullable = false)
    private Instant timestamp;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(name = "messages_json", nullable = false, columnDefinition = "LONGTEXT")
    private String messagesJson = "[]";

    // Legacy column fallback to keep old rows readable after schema changes.
    @Column(name = "messages", insertable = false, updatable = false, columnDefinition = "LONGTEXT")
    private String legacyMessagesJson;

    @Transient
    private List<String> messages = new ArrayList<>();
    @Transient
    private boolean messagesLoaded;

    @Column(name = "seance_id")
    private Integer seanceId;

    public WarningEventEntity() {
    }

    @SuppressWarnings("unchecked")
    public static WarningEventEntity fromMap(Map<String, Object> dto) {
        WarningEventEntity e = new WarningEventEntity();
        if (dto == null) {
            return e;
        }
        Object id = dto.get("id");
        if (id != null) {
            e.setId(id.toString());
        }
        Object timestamp = dto.get("timestamp");
        if (timestamp instanceof Instant instant) {
            e.setTimestamp(instant);
        }
        Object source = dto.get("source");
        if (source != null) {
            e.setSource(source.toString());
        }
        Object severity = dto.get("severity");
        if (severity != null) {
            e.setSeverity(severity.toString());
        }
        Object messages = dto.get("messages");
        if (messages instanceof List<?> list) {
            List<String> converted = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    converted.add(item.toString());
                }
            }
            e.setMessages(converted);
        } else {
            e.setMessages(new ArrayList<>());
        }
        Object seanceId = dto.get("seanceId");
        if (seanceId instanceof Number n) {
            e.setSeanceId(n.intValue());
        }
        return e;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", id);
        dto.put("timestamp", timestamp);
        dto.put("source", source);
        dto.put("severity", severity);
        dto.put("messages", new ArrayList<>(getMessages()));
        dto.put("seanceId", seanceId);
        return dto;
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
        if (!messagesLoaded) {
            String sourceJson = (messagesJson != null && !messagesJson.isBlank())
                    ? messagesJson
                    : legacyMessagesJson;
            messages = parseMessagesJson(sourceJson);
            messagesLoaded = true;
        }
        return messages;
    }

    public void setMessages(List<String> messages) {
        List<String> safeMessages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        this.messages = safeMessages;
        this.messagesJson = toMessagesJson(safeMessages);
        this.messagesLoaded = true;
    }

    public Integer getSeanceId() {
        return seanceId;
    }

    public void setSeanceId(Integer seanceId) {
        this.seanceId = seanceId;
    }

    private static String toMessagesJson(List<String> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static List<String> parseMessagesJson(String value) {
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<String> parsed = OBJECT_MAPPER.readValue(value, LIST_OF_STRING);
            return parsed != null ? new ArrayList<>(parsed) : new ArrayList<>();
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }
}

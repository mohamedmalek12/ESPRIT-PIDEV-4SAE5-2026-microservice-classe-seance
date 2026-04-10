package tn.esprit.classeseance.dto;

import java.util.List;

/**
 * POST /api/warnings/ingest — e.g. material stock warnings forwarded from Angular or other services.
 */
public class WarningIngestRequest {

    private String source;
    private List<String> messages;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<String> getMessages() {
        return messages;
    }

    public void setMessages(List<String> messages) {
        this.messages = messages;
    }
}

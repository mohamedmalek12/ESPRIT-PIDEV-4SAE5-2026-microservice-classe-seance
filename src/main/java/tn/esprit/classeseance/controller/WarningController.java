package tn.esprit.classeseance.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.classeseance.service.SeanceService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/warnings")
@CrossOrigin(origins = "*")
public class WarningController {

    private final SeanceService seanceService;

    public WarningController(SeanceService seanceService) {
        this.seanceService = seanceService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getRecent() {
        return ResponseEntity.ok(seanceService.getRecentWarnings());
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Boolean>> clear() {
        seanceService.clearWarningsHistory();
        return ResponseEntity.ok(Map.of("cleared", true));
    }

    @PostMapping("/ingest")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Boolean>> ingest(@RequestBody Map<String, Object> body) {
        List<String> messages = null;
        String source = null;
        if (body != null) {
            Object sourceObj = body.get("source");
            source = sourceObj != null ? sourceObj.toString() : null;
            Object messagesObj = body.get("messages");
            if (messagesObj instanceof List<?> rawMessages) {
                messages = rawMessages.stream()
                        .filter(item -> item != null)
                        .map(Object::toString)
                        .toList();
            }
        }
        if (messages == null || messages.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("accepted", false));
        }
        seanceService.publishExternalWarnings(source, messages);
        return ResponseEntity.ok(Map.of("accepted", true));
    }
}

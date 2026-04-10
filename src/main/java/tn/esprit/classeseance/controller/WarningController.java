package tn.esprit.classeseance.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.classeseance.dto.WarningEventDto;
import tn.esprit.classeseance.dto.WarningIngestRequest;
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
    public ResponseEntity<List<WarningEventDto>> getRecent() {
        return ResponseEntity.ok(seanceService.getRecentWarnings());
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Boolean>> clear() {
        seanceService.clearWarningsHistory();
        return ResponseEntity.ok(Map.of("cleared", true));
    }

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Boolean>> ingest(@RequestBody WarningIngestRequest body) {
        if (body == null || body.getMessages() == null || body.getMessages().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("accepted", false));
        }
        seanceService.publishExternalWarnings(body.getSource(), body.getMessages());
        return ResponseEntity.ok(Map.of("accepted", true));
    }
}

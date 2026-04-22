package tn.esprit.classeseance.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import tn.esprit.classeseance.entity.Seance;
import tn.esprit.classeseance.service.SeanceService;
import tn.esprit.classeseance.repository.SeanceRepository;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seances")
public class SeanceController {

    private final SeanceService seanceService;
    private final SeanceRepository seanceRepository;

    public SeanceController(SeanceService seanceService, SeanceRepository seanceRepository) {
        this.seanceService = seanceService;
        this.seanceRepository = seanceRepository;
    }

    @GetMapping
    public ResponseEntity<List<Seance>> getAll() {
        return ResponseEntity.ok(seanceService.findAll());
    }

    @GetMapping("/stats")
    public ResponseEntity<java.util.Map<String, Object>> getStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();

        long totalClasses = seanceService.countClasses();
        stats.put("totalClasses", totalClasses);

        long totalSalles = 0;
        try {
            List<Map<String, Object>> salles = seanceService.getAllSalles();
            totalSalles = salles.size();
        } catch (Exception e) {
            // Ignore if service is unavailable and return 0
        }
        stats.put("totalSalles", totalSalles);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Seance> getById(@PathVariable("id") Integer id) {
        return seanceService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/classe/{classeId}")
    public ResponseEntity<List<Map<String, Object>>> getByClasse(@PathVariable("classeId") Integer classeId) {
        List<Seance> list = seanceService.findByClasseId(classeId);
        List<Map<String, Object>> dtos = list.stream().map(this::toResponse).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Endpoint appelé par Angular pour peupler le dropdown des salles.
     * Appelle salles-materiels via RPC RabbitMQ en interne.
     * GET /api/seances/salles
     */
    @GetMapping("/salles")
    public ResponseEntity<?> getSalles() {
        try {
            List<Map<String, Object>> salles = seanceService.getAllSalles();
            return ResponseEntity.ok(salles);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Service salles-materiels indisponible : " + e.getMessage());
        }
    }

    /**
     * GET /api/seances/salles/occupees?debut=...&fin=...&excludeId=...
     */
    @GetMapping("/salles/occupees")
    public ResponseEntity<List<Integer>> getOccupiedSalles(
            @RequestParam("debut") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime debut,
            @RequestParam("fin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin,
            @RequestParam(value = "excludeId", required = false) Integer excludeId) {
        List<Integer> occupiedIds;
        if (excludeId != null) {
            occupiedIds = seanceRepository.findOccupiedSalleIdsExcludingId(debut, fin, excludeId);
        } else {
            occupiedIds = seanceRepository.findOccupiedSalleIds(debut, fin);
        }
        return ResponseEntity.ok(occupiedIds);
    }

    /**
     * GET /api/seances/classes/occupees?debut=...&fin=...&excludeId=...
     */
    @GetMapping("/classes/occupees")
    public ResponseEntity<List<Integer>> getOccupiedClasses(
            @RequestParam("debut") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime debut,
            @RequestParam("fin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin,
            @RequestParam(value = "excludeId", required = false) Integer excludeId) {
        List<Integer> occupiedIds;
        if (excludeId != null) {
            occupiedIds = seanceRepository.findOccupiedClasseIdsExcludingId(debut, fin, excludeId);
        } else {
            occupiedIds = seanceRepository.findOccupiedClasseIds(debut, fin);
        }
        return ResponseEntity.ok(occupiedIds);
    }

    /**
     * POST /api/seances?classeId=3
     * Vérifie automatiquement la disponibilité de la salle avant de sauvegarder.
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Seance seance,
            @RequestParam(value = "classeId", required = false) Integer classeId) {
        try {
            Map<String, Object> result = seanceService.save(seance, classeId);
            Seance saved = (Seance) result.get("seance");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "seance", toResponse(saved),
                    "warnings", result.getOrDefault("warnings", List.of())
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Erreur"));
        }
    }

    /**
     * PUT /api/seances/1?classeId=3
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable("id") Integer id,
            @RequestBody Seance seance,
            @RequestParam(value = "classeId", required = false) Integer classeId) {
        try {
            Map<String, Object> result = seanceService.update(id, seance, classeId);
            Seance updated = (Seance) result.get("seance");
            return ResponseEntity.ok(Map.of(
                    "seance", toResponse(updated),
                    "warnings", result.getOrDefault("warnings", List.of())
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Erreur"));
        }
    }

    @PostMapping("/{seanceId}/classe/{classeId}")
    public ResponseEntity<Seance> assignerClasse(@PathVariable("seanceId") Integer seanceId,
            @PathVariable("classeId") Integer classeId) {
        try {
            Seance updated = seanceService.assignerClasse(seanceId, classeId);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Integer id) {
        try {
            seanceService.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/planning/generate/{classeId}")
    public ResponseEntity<?> generateWeeklyPlanning(@PathVariable("classeId") Integer classeId) {
        try {
            List<Seance> planning = seanceService.generateWeeklyPlanning(classeId);
            List<Map<String, Object>> dtos = planning.stream().map(this::toResponse).toList();
            return ResponseEntity.ok(dtos);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Erreur"));
        }
    }

    private Map<String, Object> toResponse(Seance s) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", s.getId());
        response.put("dateDebut", s.getDateDebut());
        response.put("dateFin", s.getDateFin());
        response.put("type", s.getType());
        response.put("jour", s.getJour());
        response.put("salleId", s.getSalleId());
        if (s.getSalleId() != null) {
            Map<String, Object> salle = seanceService.getSalleById(s.getSalleId());
            if (salle != null && salle.get("nom") != null) {
                response.put("salleNom", salle.get("nom").toString());
            }
        }
        if (s.getClasse() != null) {
            response.put("classeId", s.getClasse().getId());
            response.put("classeNom", s.getClasse().getNom());
        }
        return response;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(HttpMessageNotReadableException e) {
        String msg = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage()
                : "Corps de requête invalide (dateDebut, dateFin au format ISO, type: PRESENTIEL ou EN_LIGNE)";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        e.printStackTrace();
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Erreur serveur"));
    }
}
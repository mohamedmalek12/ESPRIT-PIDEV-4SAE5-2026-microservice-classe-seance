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
import java.util.HashMap;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/seances")
public class SeanceController {

    // 1. Définition des constantes pour éviter les duplications (Literals)
    private static final String SEANCE_KEY = "seance";
    private static final String WARNINGS_KEY = "warnings";
    private static final String MESSAGE_KEY = "message";
    private static final String ERREUR_LABEL = "Erreur";

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
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        long totalClasses = seanceService.countClasses();
        stats.put("totalClasses", totalClasses);

        long totalSalles = 0;
        try {
            List<Map<String, Object>> salles = seanceService.getAllSalles();
            totalSalles = salles.size();
        } catch (RuntimeException e) {
            // Ignore if service is unavailable
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

    // 2. Remplacement de ResponseEntity<?> par ResponseEntity<Object>
    @GetMapping("/salles")
    public ResponseEntity<Object> getSalles() {
        try {
            List<Map<String, Object>> salles = seanceService.getAllSalles();
            return ResponseEntity.ok(salles);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Service salles-materiels indisponible : " + e.getMessage());
        }
    }

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

    @PostMapping
    public ResponseEntity<Object> create(@RequestBody Seance seance,
                                         @RequestParam(value = "classeId", required = false) Integer classeId) {
        try {
            Map<String, Object> result = seanceService.save(seance, classeId);
            Seance saved = (Seance) result.get(SEANCE_KEY);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    SEANCE_KEY, toResponse(saved),
                    WARNINGS_KEY, result.getOrDefault(WARNINGS_KEY, List.of())
            ));
        } catch (IllegalArgumentException e) {
            String errorMsg = (e.getMessage() != null) ? e.getMessage() : ERREUR_LABEL;
            return ResponseEntity.badRequest().body(Map.of(MESSAGE_KEY, errorMsg));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable("id") Integer id,
                                         @RequestBody Seance seance,
                                         @RequestParam(value = "classeId", required = false) Integer classeId) {
        try {
            Map<String, Object> result = seanceService.update(id, seance, classeId);
            Seance updated = (Seance) result.get(SEANCE_KEY);
            return ResponseEntity.ok(Map.of(
                    SEANCE_KEY, toResponse(updated),
                    WARNINGS_KEY, result.getOrDefault(WARNINGS_KEY, List.of())
            ));
        } catch (IllegalArgumentException e) {
            String errorMsg = (e.getMessage() != null) ? e.getMessage() : ERREUR_LABEL;
            return ResponseEntity.badRequest().body(Map.of(MESSAGE_KEY, errorMsg));
        }
    }

    @PostMapping("/{seanceId}/classe/{classeId}")
    public ResponseEntity<Seance> assignerClasse(@PathVariable("seanceId") Integer seanceId,
                                                 @PathVariable("classeId") Integer classeId) {
        try {
            Seance updated = seanceService.assignerClasse(seanceId, classeId);
            return ResponseEntity.ok(updated);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Integer id) {
        try {
            seanceService.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/planning/generate/{classeId}")
    public ResponseEntity<Object> generateWeeklyPlanning(@PathVariable("classeId") Integer classeId) {
        try {
            List<Seance> planning = seanceService.generateWeeklyPlanning(classeId);
            List<Map<String, Object>> dtos = planning.stream().map(this::toResponse).toList();
            return ResponseEntity.ok(dtos);
        } catch (IllegalArgumentException e) {
            String errorMsg = (e.getMessage() != null) ? e.getMessage() : ERREUR_LABEL;
            return ResponseEntity.badRequest().body(Map.of(MESSAGE_KEY, errorMsg));
        }
    }

    private Map<String, Object> toResponse(Seance s) {
        Map<String, Object> response = new HashMap<>();
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
        // 3. Correction de la condition "Always True" en simplifiant l'accès au message
        String msg = "Corps de requête invalide (dateDebut, dateFin au format ISO, type: PRESENTIEL ou EN_LIGNE)";
        if (e.getMostSpecificCause() != null && e.getMostSpecificCause().getMessage() != null) {
            msg = e.getMostSpecificCause().getMessage();
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(MESSAGE_KEY, msg));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleException(RuntimeException e) {
        String errorMsg = (e.getMessage() != null) ? e.getMessage() : "Erreur serveur";
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(MESSAGE_KEY, errorMsg));
    }
}
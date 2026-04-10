package tn.esprit.classeseance.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.classeseance.dto.SalleDTO;
import tn.esprit.classeseance.dto.SeanceSaveResult;
import tn.esprit.classeseance.dto.SeanceResponse;
import tn.esprit.classeseance.entity.Seance;
import tn.esprit.classeseance.service.SeanceService;
import tn.esprit.classeseance.repository.SeanceRepository;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seances")
@CrossOrigin(origins = "*")
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
            List<SalleDTO> salles = seanceService.getAllSalles();
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
    public ResponseEntity<List<SeanceResponse>> getByClasse(@PathVariable("classeId") Integer classeId) {
        List<Seance> list = seanceService.findByClasseId(classeId);
        List<SeanceResponse> dtos = list.stream().map(this::toResponse).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Endpoint appelé par Angular pour peupler le dropdown des salles.
     * Appelle salles-materiels:8088 via RestTemplate en interne.
     * GET /api/seances/salles
     */
    @GetMapping("/salles")
    public ResponseEntity<?> getSalles() {
        try {
            List<SalleDTO> salles = seanceService.getAllSalles();
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
            SeanceSaveResult result = seanceService.save(seance, classeId);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "seance", toResponse(result.getSeance()),
                    "warnings", result.getWarnings()
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
            SeanceSaveResult result = seanceService.update(id, seance, classeId);
            return ResponseEntity.ok(Map.of(
                    "seance", toResponse(result.getSeance()),
                    "warnings", result.getWarnings()
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
            List<SeanceResponse> dtos = planning.stream().map(this::toResponse).toList();
            return ResponseEntity.ok(dtos);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Erreur"));
        }
    }

    private SeanceResponse toResponse(Seance s) {
        SeanceResponse r = new SeanceResponse();
        r.setId(s.getId());
        r.setDateDebut(s.getDateDebut());
        r.setDateFin(s.getDateFin());
        r.setType(s.getType());
        r.setJour(s.getJour());
        r.setSalleId(s.getSalleId());
        if (s.getSalleId() != null) {
            SalleDTO salle = seanceService.getSalleById(s.getSalleId());
            if (salle != null) r.setSalleNom(salle.getNom());
        }
        if (s.getClasse() != null) {
            r.setClasseId(s.getClasse().getId());
            r.setClasseNom(s.getClasse().getNom());
        }
        return r;
    }
}
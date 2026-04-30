package tn.esprit.classeseance.service;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.classeseance.integration.IntegrationQueues;
import tn.esprit.classeseance.entity.Classe;
import tn.esprit.classeseance.entity.Seance;
import tn.esprit.classeseance.entity.WarningEventEntity;
import tn.esprit.classeseance.repository.ClasseRepository;
import tn.esprit.classeseance.repository.SeanceRepository;
import tn.esprit.classeseance.repository.WarningEventRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class SeanceService {

    private static final String STOMP_TOPIC_WARNINGS = "/topic/warnings";
    private static final int MAX_STORED_WARNINGS = 500;

    private final SeanceRepository seanceRepository;
    private final ClasseRepository classeRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final WarningEventRepository warningEventRepository;
    private final RabbitTemplate rabbitTemplate;

    public SeanceService(SeanceRepository seanceRepository,
            ClasseRepository classeRepository,
            SimpMessagingTemplate messagingTemplate,
            WarningEventRepository warningEventRepository,
            RabbitTemplate rabbitTemplate) {
        this.seanceRepository = seanceRepository;
        this.classeRepository = classeRepository;
        this.messagingTemplate = messagingTemplate;
        this.warningEventRepository = warningEventRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    /** For GET /api/warnings — persisted in MySQL (last 500 by time). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRecentWarnings() {
        return warningEventRepository.findTop500ByOrderByTimestampDesc().stream()
                .map(WarningEventEntity::toMap)
                .toList();
    }

    /** For DELETE /api/warnings */
    @Transactional
    public void clearWarningsHistory() {
        warningEventRepository.deleteAllWarnings();
    }

    private void publishSessionWarningsToStomp(Integer seanceId, List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        WarningEventMessage event = buildWarningEvent("SESSION", seanceId, List.copyOf(messages));
        storeAndBroadcast(event);
    }

    /**
     * Ingest warnings from other apps (e.g. material stock on port 8088) and broadcast like session warnings.
     */
    @Transactional
    public void publishExternalWarnings(String source, List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        String eventSource = source != null && !source.isBlank() ? source : "APP";
        WarningEventMessage event = buildWarningEvent(eventSource, null, List.copyOf(messages));
        storeAndBroadcast(event);
    }

    private void storeAndBroadcast(WarningEventMessage event) {
        warningEventRepository.save(WarningEventEntity.fromMap(event.toMap()));
        enforceMaxStoredWarnings();
        messagingTemplate.convertAndSend(STOMP_TOPIC_WARNINGS, event);
    }

    private static WarningEventMessage buildWarningEvent(String source, Integer seanceId, List<String> messages) {
        return new WarningEventMessage(
                UUID.randomUUID().toString(),
                Instant.now(),
                source,
                "WARNING",
                messages,
                seanceId);
    }

    /**
     * Keeps at most {@link #MAX_STORED_WARNINGS} rows (oldest removed first).
     */
    private void enforceMaxStoredWarnings() {
        long count = warningEventRepository.count();
        if (count <= MAX_STORED_WARNINGS) {
            return;
        }
        int excess = (int) (count - MAX_STORED_WARNINGS);
        List<String> oldestIds = warningEventRepository.findIdsOldestFirst(PageRequest.of(0, excess));
        if (!oldestIds.isEmpty()) {
            warningEventRepository.deleteAllByIdInBatch(oldestIds);
        }
    }

    public List<Seance> findAll() {
        return seanceRepository.findAll();
    }

    public long countClasses() {
        return classeRepository.count();
    }

    public Optional<Seance> findById(Integer id) {
        return seanceRepository.findById(id);
    }

    public List<Seance> findByClasseId(Integer classeId) {
        return seanceRepository.findByClasseId(classeId);
    }

    /**
     * Récupère toutes les salles depuis salles-materiels via RPC RabbitMQ.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAllSalles() {
        Object reply = rabbitTemplate.convertSendAndReceive(
                "", IntegrationQueues.SALLE_RPC, Map.of("action", "all"));
        if (!(reply instanceof Map<?, ?> response)) {
            return List.of();
        }
        Object salles = response.get("salles");
        if (!(salles instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> mapEntry) {
                result.add((Map<String, Object>) mapEntry);
            }
        }
        return result;
    }

    /**
     * Récupère une salle par ID depuis salles-materiels via RPC RabbitMQ.
     *
     * @return la salle ou null si indisponible / non trouvée
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSalleById(Integer id) {
        if (id == null || id <= 0) {
            return null;
        }
        Object reply = rabbitTemplate.convertSendAndReceive(
                "", IntegrationQueues.SALLE_RPC, Map.of("action", "byId", "id", id));
        if (!(reply instanceof Map<?, ?> response)) {
            return null;
        }
        Object salle = response.get("salle");
        if (!(salle instanceof Map<?, ?> salleMap)) {
            return null;
        }
        return (Map<String, Object>) salleMap;
    }

    @Transactional
    public Map<String, Object> save(Seance seance, Integer classeId) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. Vérifier que la salle existe dans l'autre microservice
        if (seance.getSalleId() != null) {
            Map<String, Object> salle = getSalleById(seance.getSalleId());
            if (salle == null) {
                errors.add("Room not found with id: " + seance.getSalleId());
            } else {
                // 2. Vérifier qu'aucune séance n'occupe déjà cette salle sur ce créneau
                boolean occupee = seanceRepository.existsBySalleIdAndCreneau(
                        seance.getSalleId(),
                        seance.getDateDebut(),
                        seance.getDateFin());
                if (occupee) {
                    errors.add("The room '" + getSalleNom(salle) + "' is already occupied for this time slot.");
                }
            }
        }

        if (seance.getDateDebut() == null || seance.getDateFin() == null || !seance.getDateFin().isAfter(seance.getDateDebut())) {
            errors.add("Invalid time range: end date/time must be strictly after start date/time.");
        }

        // 3. Assigner la classe si fournie
        if (classeId != null) {
            Classe classe = classeRepository.findById(classeId)
                    .orElse(null);
            if (classe == null) {
                errors.add("Class not found with id: " + classeId);
            } else {
                boolean classeOccupee = seanceRepository.existsByClasseIdAndCreneau(
                        classeId,
                        seance.getDateDebut(),
                        seance.getDateFin());
                if (classeOccupee) {
                    errors.add("The class '" + classe.getNom() + "' already has a session in this time slot.");
                } else {
                    seance.setClasse(classe);
                }
            }
        }

        validateDailyLimitsAndWarnings(seance, classeId, null, errors, warnings);

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", errors));
        }

        Seance saved = seanceRepository.save(seance);
        addMaintenanceWarningsForSeance(saved, warnings);
        publishSessionWarningsToStomp(saved.getId(), warnings);
        return Map.of("seance", saved, "warnings", warnings);
    }

    @Transactional
    public Map<String, Object> update(Integer id, Seance seance, Integer classeId) {
        return seanceRepository.findById(id)
                .map(existing -> {
                    List<String> errors = new ArrayList<>();
                    List<String> warnings = new ArrayList<>();

                    // Vérifier disponibilité salle si elle a changé
                    if (seance.getSalleId() != null) {
                        Map<String, Object> salle = getSalleById(seance.getSalleId());
                        if (salle == null) {
                            errors.add("Room not found with id: " + seance.getSalleId());
                        } else {
                            boolean occupee = seanceRepository.existsBySalleIdAndCreneauExcludingId(
                                    seance.getSalleId(),
                                    seance.getDateDebut(),
                                    seance.getDateFin(),
                                    id);
                            if (occupee) {
                                errors.add(
                                        "The room '" + getSalleNom(salle) + "' is already occupied for this time slot.");
                            } else {
                                existing.setSalleId(seance.getSalleId());
                            }
                        }
                    }

                    existing.setDateDebut(seance.getDateDebut());
                    existing.setDateFin(seance.getDateFin());
                    existing.setType(seance.getType());
                    existing.setJour(seance.getJour());

                    if (classeId != null) {
                        Classe classe = classeRepository.findById(classeId)
                                .orElse(null);
                        if (classe == null) {
                            errors.add("Class not found with id: " + classeId);
                        } else {
                            boolean classeOccupee = seanceRepository.existsByClasseIdAndCreneauExcludingId(
                                    classeId,
                                    seance.getDateDebut(),
                                    seance.getDateFin(),
                                    id);
                            if (classeOccupee) {
                                errors.add("The class '" + classe.getNom()
                                        + "' already has a session in this time slot.");
                            } else {
                                existing.setClasse(classe);
                            }
                        }
                    } else {
                        existing.setClasse(null);
                    }

                    Integer effectiveClasseId = existing.getClasse() != null ? existing.getClasse().getId() : null;
                    validateDailyLimitsAndWarnings(existing, effectiveClasseId, id, errors, warnings);

                    if (!errors.isEmpty()) {
                        throw new IllegalArgumentException(String.join("\n", errors));
                    }

                    Seance updated = seanceRepository.save(existing);
                    addMaintenanceWarningsForSeance(updated, warnings);
                    publishSessionWarningsToStomp(updated.getId(), warnings);
                    return Map.of("seance", updated, "warnings", warnings);
                })
                .orElseThrow(() -> new NoSuchElementException("Session not found with id: " + id));
    }

    @Transactional
    public Seance assignerClasse(Integer seanceId, Integer classeId) {
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new NoSuchElementException("Session not found with id: " + seanceId));
        Classe classe = classeRepository.findById(classeId)
                .orElseThrow(() -> new NoSuchElementException("Class not found with id: " + classeId));
        seance.setClasse(classe);
        return seanceRepository.save(seance);
    }

    @Transactional
    public void deleteById(Integer id) {
        if (!seanceRepository.existsById(id)) {
            throw new NoSuchElementException("Session not found with id: " + id);
        }
        seanceRepository.deleteById(id);
    }

    @Transactional
    public List<Seance> generateWeeklyPlanning(Integer classeId) {
        Classe classe = classeRepository.findById(classeId)
                .orElseThrow(() -> new NoSuchElementException("Class not found with id: " + classeId));

        java.time.LocalDate nextMonday = java.time.LocalDate.now()
                .with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));

        List<Seance> createdSeances = new java.util.ArrayList<>();

        for (int i = 0; i < 5; i++) {
            java.time.LocalDate currentDay = nextMonday.plusDays(i);

            // Matin : 09h00 à 12h00
            java.time.LocalDateTime morningStart = currentDay.atTime(9, 0);
            java.time.LocalDateTime morningEnd = currentDay.atTime(12, 0);

            if (!seanceRepository.existsByClasseIdAndCreneau(classeId, morningStart, morningEnd)) {
                Seance s1 = new Seance();
                s1.setDateDebut(morningStart);
                s1.setDateFin(morningEnd);
                s1.setType(tn.esprit.classeseance.entity.TypeSeance.PRESENTIEL);
                s1.setJour(currentDay.getDayOfWeek().name());
                s1.setClasse(classe);
                createdSeances.add(seanceRepository.save(s1));
            }

            // Après-midi : 14h00 à 17h00
            java.time.LocalDateTime afternoonStart = currentDay.atTime(14, 0);
            java.time.LocalDateTime afternoonEnd = currentDay.atTime(17, 0);

            if (!seanceRepository.existsByClasseIdAndCreneau(classeId, afternoonStart, afternoonEnd)) {
                Seance s2 = new Seance();
                s2.setDateDebut(afternoonStart);
                s2.setDateFin(afternoonEnd);
                s2.setType(tn.esprit.classeseance.entity.TypeSeance.PRESENTIEL);
                s2.setJour(currentDay.getDayOfWeek().name());
                s2.setClasse(classe);
                createdSeances.add(seanceRepository.save(s2));
            }
        }
        return createdSeances;
    }

    private void validateDailyLimitsAndWarnings(Seance seance, Integer classeId, Integer excludeId,
            List<String> errors, List<String> warnings) {
        if (seance.getDateDebut() == null || seance.getDateFin() == null || !seance.getDateFin().isAfter(seance.getDateDebut())) {
            return;
        }

        LocalDate day = seance.getDateDebut().toLocalDate();
        LocalDateTime startOfDay = day.atStartOfDay();
        LocalDateTime endOfDay = day.plusDays(1).atStartOfDay();

        if (classeId != null) {
            long classeCount = (excludeId == null)
                    ? seanceRepository.countByClasseIdInDay(classeId, startOfDay, endOfDay)
                    : seanceRepository.countByClasseIdInDayExcludingId(classeId, startOfDay, endOfDay, excludeId);
            if (classeCount >= 2) {
                String className = classeRepository.findById(classeId).map(Classe::getNom).orElse("this class");
                errors.add("Class '" + className + "' already has 2 sessions scheduled today.");
            }

            List<Seance> daySeances = (excludeId == null)
                    ? seanceRepository.findByClasseIdInDay(classeId, startOfDay, endOfDay)
                    : seanceRepository.findByClasseIdInDayExcludingId(classeId, startOfDay, endOfDay, excludeId);
            addScheduleGapWarnings(daySeances, seance, warnings);

            if (startsBefore08(seance) || endsAfter18(seance)) {
                List<Seance> forEarlyLate = new ArrayList<>(daySeances);
                forEarlyLate.add(seance);
                String className = classeRepository.findById(classeId).map(Classe::getNom).orElse("this class");
                String dateStr = DateTimeFormatter.ofPattern("dd/MM/yyyy").format(day);

                long earlyCount = forEarlyLate.stream().filter(SeanceService::startsBefore08).count();
                if (earlyCount == 1) {
                    warnings.add("1 session of class '" + className + "' on " + dateStr
                            + " is scheduled before 08:00.");
                } else if (earlyCount > 1) {
                    warnings.add(earlyCount + " sessions of class '" + className + "' on " + dateStr
                            + " are scheduled before 08:00.");
                }

                long lateCount = forEarlyLate.stream().filter(SeanceService::endsAfter18).count();
                if (lateCount == 1) {
                    warnings.add("1 session of class '" + className + "' on " + dateStr
                            + " is scheduled after 18:00.");
                } else if (lateCount > 1) {
                    warnings.add(lateCount + " sessions of class '" + className + "' on " + dateStr
                            + " are scheduled after 18:00.");
                }
            }
        } else if (startsBefore08(seance)) {
            warnings.add("Warning: session is scheduled before 08:00.");
        } else if (endsAfter18(seance)) {
            warnings.add("Warning: session is scheduled after 18:00.");
        }

        if (seance.getSalleId() != null) {
            long salleCount = (excludeId == null)
                    ? seanceRepository.countBySalleIdInDay(seance.getSalleId(), startOfDay, endOfDay)
                    : seanceRepository.countBySalleIdInDayExcludingId(seance.getSalleId(), startOfDay, endOfDay, excludeId);
            if (salleCount >= 2) {
                Map<String, Object> salle = getSalleById(seance.getSalleId());
                String salleNom = getSalleNom(salle);
                String roomName = (salleNom != null && !salleNom.isBlank())
                        ? salleNom
                        : "this room";
                errors.add("There are already 2 sessions scheduled in room '" + roomName + "' today.");
            }
        }

    }

    private static String getSalleNom(Map<String, Object> salle) {
        if (salle == null) {
            return null;
        }
        Object value = salle.get("nom");
        return value != null ? value.toString() : null;
    }

    private static boolean startsBefore08(Seance s) {
        if (s.getDateDebut() == null || s.getDateFin() == null) {
            return false;
        }
        LocalTime startTime = s.getDateDebut().toLocalTime();
        return startTime.isBefore(LocalTime.of(8, 0));
    }

    private static boolean endsAfter18(Seance s) {
        if (s.getDateDebut() == null || s.getDateFin() == null) {
            return false;
        }
        LocalTime endTime = s.getDateFin().toLocalTime();
        return endTime.isAfter(LocalTime.of(18, 0));
    }

    private void addScheduleGapWarnings(List<Seance> daySeances, Seance newSeance, List<String> warnings) {
        List<Seance> all = new ArrayList<>(daySeances);
        all.add(newSeance);
        all.sort((a, b) -> a.getDateDebut().compareTo(b.getDateDebut()));

        for (int i = 1; i < all.size(); i++) {
            Seance previous = all.get(i - 1);
            Seance current = all.get(i);
            Duration gap = Duration.between(previous.getDateFin(), current.getDateDebut());
            long gapMinutes = gap.toMinutes();
            if (gapMinutes < 60) {
                warnings.add("Warning: less than 1 hour between two sessions on the same day.");
            } else if (gapMinutes > 300) {
                warnings.add("Warning: large gap between two sessions on the same day.");
            }
        }
    }

    private void addMaintenanceWarningsForSeance(Seance seance, List<String> warnings) {
        if (seance.getSalleId() == null || seance.getDateDebut() == null || seance.getDateFin() == null
                || !seance.getDateFin().isAfter(seance.getDateDebut())) {
            return;
        }

        long durationMinutes = Duration.between(seance.getDateDebut(), seance.getDateFin()).toMinutes();
        if (durationMinutes <= 0) {
            return;
        }
        double durationHours = durationMinutes / 60.0;

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("salleId", seance.getSalleId());
            request.put("hours", durationHours);
            Object reply = rabbitTemplate.convertSendAndReceive("", IntegrationQueues.MATERIEL_USAGE_RPC, request);
            if (!(reply instanceof Map<?, ?> response)) {
                return;
            }
            if (Boolean.TRUE.equals(response.get("invalidHours")) || Boolean.TRUE.equals(response.get("error"))) {
                return;
            }
            Object alertObj = response.get("warnings");
            if (alertObj instanceof List<?> alertList) {
                for (Object alert : alertList) {
                    if (alert != null) {
                        warnings.add(alert.toString());
                    }
                }
            }
        } catch (AmqpException e) {
            // Broker ou salles-materiels indisponible : pas d'alerte technique côté UI.
        } catch (ClassCastException e) {
            // Erreur de désérialisation ou autre : ignorer.
        }
    }

    private static final class WarningEventMessage {
        private final String id;
        private final Instant timestamp;
        private final String source;
        private final String severity;
        private final List<String> messages;
        private final Integer seanceId;

        private WarningEventMessage(
                String id,
                Instant timestamp,
                String source,
                String severity,
                List<String> messages,
                Integer seanceId) {
            this.id = id;
            this.timestamp = timestamp;
            this.source = source;
            this.severity = severity;
            this.messages = messages;
            this.seanceId = seanceId;
        }

        public String getId() {
            return id;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public String getSource() {
            return source;
        }

        public String getSeverity() {
            return severity;
        }

        public List<String> getMessages() {
            return messages;
        }

        public Integer getSeanceId() {
            return seanceId;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("timestamp", timestamp);
            map.put("source", source);
            map.put("severity", severity);
            map.put("messages", messages);
            map.put("seanceId", seanceId);
            return map;
        }
    }
}
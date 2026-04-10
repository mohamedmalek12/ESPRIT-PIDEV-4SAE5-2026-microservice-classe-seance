package tn.esprit.classeseance.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tn.esprit.classeseance.dto.SalleDTO;
import tn.esprit.classeseance.dto.SeanceSaveResult;
import tn.esprit.classeseance.dto.WarningEventDto;
import tn.esprit.classeseance.entity.Classe;
import tn.esprit.classeseance.entity.Seance;
import tn.esprit.classeseance.repository.ClasseRepository;
import tn.esprit.classeseance.repository.SeanceRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class SeanceService {

    private static final String SALLES_URL = "http://localhost:8088/api/salles";
    private static final String MATERIELS_URL = "http://localhost:8088/api/materiels";
    private static final String STOMP_TOPIC_WARNINGS = "/topic/warnings";
    private static final int MAX_STORED_WARNINGS = 500;

    private final SeanceRepository seanceRepository;
    private final ClasseRepository classeRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private final List<WarningEventDto> recentWarnings = new ArrayList<>();
    private final ReentrantReadWriteLock warningsLock = new ReentrantReadWriteLock();

    // RestTemplate natif Spring — aucune dépendance supplémentaire
    private final RestTemplate restTemplate = new RestTemplate();

    public SeanceService(SeanceRepository seanceRepository,
            ClasseRepository classeRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.seanceRepository = seanceRepository;
        this.classeRepository = classeRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /** For GET /api/warnings */
    public List<WarningEventDto> getRecentWarnings() {
        warningsLock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(recentWarnings));
        } finally {
            warningsLock.readLock().unlock();
        }
    }

    /** For DELETE /api/warnings */
    public void clearWarningsHistory() {
        warningsLock.writeLock().lock();
        try {
            recentWarnings.clear();
        } finally {
            warningsLock.writeLock().unlock();
        }
    }

    private void publishSessionWarningsToStomp(Integer seanceId, List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        WarningEventDto event = WarningEventDto.ofSession(seanceId, List.copyOf(messages));
        storeAndBroadcast(event);
    }

    /**
     * Ingest warnings from other apps (e.g. material stock on port 8088) and broadcast like session warnings.
     */
    public void publishExternalWarnings(String source, List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        WarningEventDto event = WarningEventDto.ofExternal(source, List.copyOf(messages));
        storeAndBroadcast(event);
    }

    private void storeAndBroadcast(WarningEventDto event) {
        messagingTemplate.convertAndSend(STOMP_TOPIC_WARNINGS, event);
        warningsLock.writeLock().lock();
        try {
            recentWarnings.add(0, event);
            while (recentWarnings.size() > MAX_STORED_WARNINGS) {
                recentWarnings.remove(recentWarnings.size() - 1);
            }
        } finally {
            warningsLock.writeLock().unlock();
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
     * Récupère toutes les salles depuis salles-materiels via RestTemplate.
     * Appelé par le controller pour peupler le dropdown Angular.
     */
    public List<SalleDTO> getAllSalles() {
        SalleDTO[] salles = restTemplate.getForObject(SALLES_URL, SalleDTO[].class);
        return salles != null ? Arrays.asList(salles) : List.of();
    }

    /**
     * Récupère une salle par ID depuis salles-materiels via RestTemplate.
     * @return la salle ou null si service indisponible / non trouvée
     */
    public SalleDTO getSalleById(Integer id) {
        try {
            return restTemplate.getForObject(SALLES_URL + "/" + id, SalleDTO.class);
        } catch (RestClientException e) {
            return null;
        }
    }

    @Transactional
    public SeanceSaveResult save(Seance seance, Integer classeId) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. Vérifier que la salle existe dans l'autre microservice
        if (seance.getSalleId() != null) {
            SalleDTO salle = getSalleById(seance.getSalleId());
            if (salle == null) {
                errors.add("Room not found with id: " + seance.getSalleId());
            } else {
                // 2. Vérifier qu'aucune séance n'occupe déjà cette salle sur ce créneau
                boolean occupee = seanceRepository.existsBySalleIdAndCreneau(
                        seance.getSalleId(),
                        seance.getDateDebut(),
                        seance.getDateFin());
                if (occupee) {
                    errors.add("The room '" + salle.getNom() + "' is already occupied for this time slot.");
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
            throw new RuntimeException(String.join("\n", errors));
        }

        Seance saved = seanceRepository.save(seance);
        addMaintenanceWarningsForSeance(saved, warnings);
        publishSessionWarningsToStomp(saved.getId(), warnings);
        return new SeanceSaveResult(saved, warnings);
    }

    @Transactional
    public SeanceSaveResult update(Integer id, Seance seance, Integer classeId) {
        return seanceRepository.findById(id)
                .map(existing -> {
                    List<String> errors = new ArrayList<>();
                    List<String> warnings = new ArrayList<>();

                    // Vérifier disponibilité salle si elle a changé
                    if (seance.getSalleId() != null) {
                        SalleDTO salle = getSalleById(seance.getSalleId());
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
                                        "The room '" + salle.getNom() + "' is already occupied for this time slot.");
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
                        throw new RuntimeException(String.join("\n", errors));
                    }

                    Seance updated = seanceRepository.save(existing);
                    addMaintenanceWarningsForSeance(updated, warnings);
                    publishSessionWarningsToStomp(updated.getId(), warnings);
                    return new SeanceSaveResult(updated, warnings);
                })
                .orElseThrow(() -> new RuntimeException("Session not found with id: " + id));
    }

    @Transactional
    public Seance assignerClasse(Integer seanceId, Integer classeId) {
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new RuntimeException("Session not found with id: " + seanceId));
        Classe classe = classeRepository.findById(classeId)
                .orElseThrow(() -> new RuntimeException("Class not found with id: " + classeId));
        seance.setClasse(classe);
        return seanceRepository.save(seance);
    }

    @Transactional
    public void deleteById(Integer id) {
        if (!seanceRepository.existsById(id)) {
            throw new RuntimeException("Session not found with id: " + id);
        }
        seanceRepository.deleteById(id);
    }

    @Transactional
    public List<Seance> generateWeeklyPlanning(Integer classeId) {
        Classe classe = classeRepository.findById(classeId)
                .orElseThrow(() -> new RuntimeException("Class not found with id: " + classeId));

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
                SalleDTO salle = getSalleById(seance.getSalleId());
                String roomName = (salle != null && salle.getNom() != null && !salle.getNom().isBlank())
                        ? salle.getNom()
                        : "this room";
                errors.add("There are already 2 sessions scheduled in room '" + roomName + "' today.");
            }
        }

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
            String url = UriComponentsBuilder.fromUriString(MATERIELS_URL + "/usage/salle/{salleId}")
                    .queryParam("hours", durationHours)
                    .buildAndExpand(seance.getSalleId())
                    .toUriString();

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, null, Map.class);
            if (response == null) {
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
        } catch (RestClientException e) {
            // Service externe indisponible : ne pas polluer l'UI avec une alerte technique.
        } catch (RuntimeException e) {
            // Erreur technique inattendue : ne pas afficher d'alerte si la séance est créée/modifiée correctement.
        }
    }
}
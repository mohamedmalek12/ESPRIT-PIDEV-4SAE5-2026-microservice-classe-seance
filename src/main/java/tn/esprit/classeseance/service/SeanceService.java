package tn.esprit.classeseance.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tn.esprit.classeseance.dto.SalleDTO;
import tn.esprit.classeseance.entity.Classe;
import tn.esprit.classeseance.entity.Seance;
import tn.esprit.classeseance.repository.ClasseRepository;
import tn.esprit.classeseance.repository.SeanceRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class SeanceService {

    private static final String SALLES_URL = "http://localhost:8088/api/salles";

    private final SeanceRepository seanceRepository;
    private final ClasseRepository classeRepository;

    // RestTemplate natif Spring — aucune dépendance supplémentaire
    private final RestTemplate restTemplate = new RestTemplate();

    public SeanceService(SeanceRepository seanceRepository,
            ClasseRepository classeRepository) {
        this.seanceRepository = seanceRepository;
        this.classeRepository = classeRepository;
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
    public Seance save(Seance seance, Integer classeId) {
        List<String> errors = new java.util.ArrayList<>();

        // 1. Vérifier que la salle existe dans l'autre microservice
        if (seance.getSalleId() != null) {
            SalleDTO salle = getSalleById(seance.getSalleId());
            if (salle == null) {
                errors.add("Salle non trouvée avec l'id : " + seance.getSalleId());
            } else {
                // 2. Vérifier qu'aucune séance n'occupe déjà cette salle sur ce créneau
                boolean occupee = seanceRepository.existsBySalleIdAndCreneau(
                        seance.getSalleId(),
                        seance.getDateDebut(),
                        seance.getDateFin());
                if (occupee) {
                    errors.add("La salle '" + salle.getNom() + "' est déjà occupée sur ce créneau horaire.");
                }
            }
        }

        // 3. Assigner la classe si fournie
        if (classeId != null) {
            Classe classe = classeRepository.findById(classeId)
                    .orElse(null);
            if (classe == null) {
                errors.add("Classe non trouvée avec l'id : " + classeId);
            } else {
                boolean classeOccupee = seanceRepository.existsByClasseIdAndCreneau(
                        classeId,
                        seance.getDateDebut(),
                        seance.getDateFin());
                if (classeOccupee) {
                    errors.add("La classe '" + classe.getNom() + "' a déjà une séance prévue sur ce créneau horaire.");
                } else {
                    seance.setClasse(classe);
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new RuntimeException(String.join("\n", errors));
        }

        return seanceRepository.save(seance);
    }

    @Transactional
    public Seance update(Integer id, Seance seance, Integer classeId) {
        return seanceRepository.findById(id)
                .map(existing -> {
                    List<String> errors = new java.util.ArrayList<>();

                    // Vérifier disponibilité salle si elle a changé
                    if (seance.getSalleId() != null) {
                        SalleDTO salle = getSalleById(seance.getSalleId());
                        if (salle == null) {
                            errors.add("Salle non trouvée avec l'id : " + seance.getSalleId());
                        } else {
                            boolean occupee = seanceRepository.existsBySalleIdAndCreneauExcludingId(
                                    seance.getSalleId(),
                                    seance.getDateDebut(),
                                    seance.getDateFin(),
                                    id);
                            if (occupee) {
                                errors.add(
                                        "La salle '" + salle.getNom() + "' est déjà occupée sur ce créneau horaire.");
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
                            errors.add("Classe non trouvée avec l'id : " + classeId);
                        } else {
                            boolean classeOccupee = seanceRepository.existsByClasseIdAndCreneauExcludingId(
                                    classeId,
                                    seance.getDateDebut(),
                                    seance.getDateFin(),
                                    id);
                            if (classeOccupee) {
                                errors.add("La classe '" + classe.getNom()
                                        + "' a déjà une séance prévue sur ce créneau horaire.");
                            } else {
                                existing.setClasse(classe);
                            }
                        }
                    } else {
                        existing.setClasse(null);
                    }

                    if (!errors.isEmpty()) {
                        throw new RuntimeException(String.join("\n", errors));
                    }

                    return seanceRepository.save(existing);
                })
                .orElseThrow(() -> new RuntimeException("Séance non trouvée avec l'id : " + id));
    }

    @Transactional
    public Seance assignerClasse(Integer seanceId, Integer classeId) {
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new RuntimeException("Séance non trouvée avec l'id : " + seanceId));
        Classe classe = classeRepository.findById(classeId)
                .orElseThrow(() -> new RuntimeException("Classe non trouvée avec l'id : " + classeId));
        seance.setClasse(classe);
        return seanceRepository.save(seance);
    }

    @Transactional
    public void deleteById(Integer id) {
        if (!seanceRepository.existsById(id)) {
            throw new RuntimeException("Séance non trouvée avec l'id : " + id);
        }
        seanceRepository.deleteById(id);
    }

    @Transactional
    public List<Seance> generateWeeklyPlanning(Integer classeId) {
        Classe classe = classeRepository.findById(classeId)
                .orElseThrow(() -> new RuntimeException("Classe non trouvée avec l'id : " + classeId));

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
}
package tn.esprit.classeseance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.classeseance.entity.Seance;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SeanceRepository extends JpaRepository<Seance, Integer> {

        @Query("SELECT s FROM Seance s WHERE s.classe.id = :classeId")
        List<Seance> findByClasseId(@Param("classeId") Integer classeId);

        /**
         * Vérifie si une salle est déjà occupée sur un créneau.
         * Deux créneaux se chevauchent si : début1 < fin2 ET fin1 > début2
         */
        @Query("SELECT COUNT(s) > 0 FROM Seance s " +
                        "WHERE s.salleId = :salleId " +
                        "AND s.dateDebut < :dateFin " +
                        "AND s.dateFin > :dateDebut")
        boolean existsBySalleIdAndCreneau(
                        @Param("salleId") Integer salleId,
                        @Param("dateDebut") LocalDateTime dateDebut,
                        @Param("dateFin") LocalDateTime dateFin);

        /**
         * Même vérification en excluant la séance en cours de modification (update).
         */
        @Query("SELECT COUNT(s) > 0 FROM Seance s " +
                        "WHERE s.salleId = :salleId " +
                        "AND s.dateDebut < :dateFin " +
                        "AND s.dateFin > :dateDebut " +
                        "AND s.id <> :excludeId")
        boolean existsBySalleIdAndCreneauExcludingId(
                        @Param("salleId") Integer salleId,
                        @Param("dateDebut") LocalDateTime dateDebut,
                        @Param("dateFin") LocalDateTime dateFin,
                        @Param("excludeId") Integer excludeId);

        /**
         * Vérifie si une classe a déjà une séance sur un créneau.
         */
        @Query("SELECT COUNT(s) > 0 FROM Seance s " +
                        "WHERE s.classe.id = :classeId " +
                        "AND s.dateDebut < :dateFin " +
                        "AND s.dateFin > :dateDebut")
        boolean existsByClasseIdAndCreneau(
                        @Param("classeId") Integer classeId,
                        @Param("dateDebut") LocalDateTime dateDebut,
                        @Param("dateFin") LocalDateTime dateFin);

        /**
         * Même vérification pour la classe en excluant la séance en cours.
         */
        @Query("SELECT COUNT(s) > 0 FROM Seance s " +
                        "WHERE s.classe.id = :classeId " +
                        "AND s.dateDebut < :dateFin " +
                        "AND s.dateFin > :dateDebut " +
                        "AND s.id <> :excludeId")
        boolean existsByClasseIdAndCreneauExcludingId(
                        @Param("classeId") Integer classeId,
                        @Param("dateDebut") LocalDateTime dateDebut,
                        @Param("dateFin") LocalDateTime dateFin,
                        @Param("excludeId") Integer excludeId);

        @Query("SELECT DISTINCT s.salleId FROM Seance s " +
                        "WHERE s.dateDebut < :dateFin " +
                        "AND s.dateFin > :dateDebut " +
                        "AND s.salleId IS NOT NULL")
        List<Integer> findOccupiedSalleIds(
                        @Param("dateDebut") LocalDateTime dateDebut,
                        @Param("dateFin") LocalDateTime dateFin);

        @Query("SELECT DISTINCT s.salleId FROM Seance s " +
                        "WHERE s.dateDebut < :dateFin " +
                        "AND s.dateFin > :dateDebut " +
                        "AND s.salleId IS NOT NULL " +
                        "AND s.id <> :excludeId")
        List<Integer> findOccupiedSalleIdsExcludingId(
                        @Param("dateDebut") LocalDateTime dateDebut,
                        @Param("dateFin") LocalDateTime dateFin,
                        @Param("excludeId") Integer excludeId);

        @Query("SELECT DISTINCT s.classe.id FROM Seance s " +
                        "WHERE s.dateDebut < :dateFin " +
                        "AND s.dateFin > :dateDebut " +
                        "AND s.classe IS NOT NULL")
        List<Integer> findOccupiedClasseIds(
                        @Param("dateDebut") LocalDateTime dateDebut,
                        @Param("dateFin") LocalDateTime dateFin);

        @Query("SELECT DISTINCT s.classe.id FROM Seance s " +
                        "WHERE s.dateDebut < :dateFin " +
                        "AND s.dateFin > :dateDebut " +
                        "AND s.classe IS NOT NULL " +
                        "AND s.id <> :excludeId")
        List<Integer> findOccupiedClasseIdsExcludingId(
                        @Param("dateDebut") LocalDateTime dateDebut,
                        @Param("dateFin") LocalDateTime dateFin,
                        @Param("excludeId") Integer excludeId);
}
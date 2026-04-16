package tn.esprit.classeseance.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tn.esprit.classeseance.entity.WarningEventEntity;

import java.util.List;

@Repository
public interface WarningEventRepository extends JpaRepository<WarningEventEntity, String> {

    List<WarningEventEntity> findTop500ByOrderByTimestampDesc();

    @Query("SELECT w.id FROM WarningEventEntity w ORDER BY w.timestamp ASC")
    List<String> findIdsOldestFirst(Pageable pageable);

    @Modifying
    @Query("DELETE FROM WarningEventEntity")
    void deleteAllWarnings();
}

package tn.esprit.classeseance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.classeseance.entity.Classe;

public interface ClasseRepository extends JpaRepository<Classe, Integer> {
}

package tn.esprit.classeseance.dto;

import tn.esprit.classeseance.entity.TypeSeance;

import java.time.LocalDateTime;

/**
 * DTO pour la réponse API des séances (évite la sérialisation des entités JPA avec relations lazy).
 */
public class SeanceResponse {
    private Integer id;
    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private TypeSeance type;
    private String jour;
    private Integer salleId;
    private String salleNom;
    private Integer classeId;
    private String classeNom;

    public SeanceResponse() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public LocalDateTime getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDateTime dateDebut) { this.dateDebut = dateDebut; }
    public LocalDateTime getDateFin() { return dateFin; }
    public void setDateFin(LocalDateTime dateFin) { this.dateFin = dateFin; }
    public TypeSeance getType() { return type; }
    public void setType(TypeSeance type) { this.type = type; }
    public String getJour() { return jour; }
    public void setJour(String jour) { this.jour = jour; }
    public Integer getSalleId() { return salleId; }
    public void setSalleId(Integer salleId) { this.salleId = salleId; }
    public String getSalleNom() { return salleNom; }
    public void setSalleNom(String salleNom) { this.salleNom = salleNom; }
    public Integer getClasseId() { return classeId; }
    public void setClasseId(Integer classeId) { this.classeId = classeId; }
    public String getClasseNom() { return classeNom; }
    public void setClasseNom(String classeNom) { this.classeNom = classeNom; }
}

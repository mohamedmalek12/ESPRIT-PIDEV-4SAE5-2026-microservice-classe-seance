package tn.esprit.classeseance.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "seance")
public class Seance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "date_debut", nullable = false)
    private LocalDateTime dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDateTime dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeSeance type;

    @Column(length = 50)
    private String jour;

    /**
     * ID de la salle dans le microservice salles-materiels.
     * Pas de @ManyToOne entre microservices — on stocke uniquement l'ID.
     */
    @Column(name = "salle_id")
    private Integer salleId;

    @ManyToOne
    @JoinColumn(name = "classe_id")
    @JsonIgnoreProperties("seances")
    private Classe classe;

    public Seance() {}

    public Seance(Integer id, LocalDateTime dateDebut, LocalDateTime dateFin,
                  TypeSeance type, String jour, Integer salleId, Classe classe) {
        this.id = id;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.type = type;
        this.jour = jour;
        this.salleId = salleId;
        this.classe = classe;
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

    public Classe getClasse() { return classe; }
    public void setClasse(Classe classe) { this.classe = classe; }
}
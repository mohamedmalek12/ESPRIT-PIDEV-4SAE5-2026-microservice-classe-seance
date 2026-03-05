package tn.esprit.classeseance.dto;

/**
 * DTO représentant une Salle récupérée depuis le microservice salles-materiels
 * (port 8088).
 * Utilisé par RestTemplate pour désérialiser la réponse JSON.
 */
public class SalleDTO {

    private Integer id;
    private String nom;
    private Integer capacite;
    private boolean horsService;

    public SalleDTO() {
    }

    public SalleDTO(Integer id, String nom, Integer capacite, boolean horsService) {
        this.id = id;
        this.nom = nom;
        this.capacite = capacite;
        this.horsService = horsService;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public Integer getCapacite() {
        return capacite;
    }

    public void setCapacite(Integer capacite) {
        this.capacite = capacite;
    }

    public boolean isHorsService() {
        return horsService;
    }

    public void setHorsService(boolean horsService) {
        this.horsService = horsService;
    }
}
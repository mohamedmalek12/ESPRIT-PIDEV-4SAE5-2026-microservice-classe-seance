package tn.esprit.classeseance.dto;

import tn.esprit.classeseance.entity.Seance;

import java.util.ArrayList;
import java.util.List;

public class SeanceSaveResult {
    private Seance seance;
    private List<String> warnings = new ArrayList<>();

    public SeanceSaveResult() {
    }

    public SeanceSaveResult(Seance seance, List<String> warnings) {
        this.seance = seance;
        this.warnings = warnings;
    }

    public Seance getSeance() {
        return seance;
    }

    public void setSeance(Seance seance) {
        this.seance = seance;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}

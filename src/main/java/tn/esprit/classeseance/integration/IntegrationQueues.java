package tn.esprit.classeseance.integration;

public final class IntegrationQueues {

    private IntegrationQueues() {
    }

    /** salles-materiels → classe-seance (material warnings ingest). */
    public static final String MATERIAL_WARNINGS = "q.material.warnings";

    /** classe-seance → salles-materiels (RPC: register usage, return warnings). */
    public static final String MATERIEL_USAGE_RPC = "q.materiel.usage.rpc";

    /** classe-seance → salles-materiels (RPC: get room(s) metadata). */
    public static final String SALLE_RPC = "q.salle.rpc";
}

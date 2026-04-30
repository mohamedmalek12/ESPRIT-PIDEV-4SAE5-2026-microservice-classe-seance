package tn.esprit.classeseance.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.classeseance.entity.Classe;
import tn.esprit.classeseance.entity.Seance;
import tn.esprit.classeseance.entity.TypeSeance;
import tn.esprit.classeseance.service.SeanceService;
import tn.esprit.classeseance.repository.SeanceRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SeanceController.class)
class SeanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SeanceService seanceService;

    @MockBean
    private SeanceRepository seanceRepository;

    @Test
    void getAll_returnsOkAndJsonArray() throws Exception {
        Seance seance = new Seance();
        seance.setId(1);
        seance.setJour("LUNDI");
        seance.setType(TypeSeance.PRESENTIEL);

        when(seanceService.findAll()).thenReturn(List.of(seance));

        mockMvc.perform(get("/api/seances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jour").value("LUNDI"));
    }

    @Test
    void getById_returnsOkWhenFound() throws Exception {
        Seance seance = new Seance();
        seance.setId(1);
        seance.setJour("MARDI");

        when(seanceService.findById(1)).thenReturn(Optional.of(seance));

        mockMvc.perform(get("/api/seances/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jour").value("MARDI"));
    }

    @Test
    void getById_returns404WhenMissing() throws Exception {
        when(seanceService.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/seances/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_returns201() throws Exception {
        Seance savedSeance = new Seance();
        savedSeance.setId(2);
        savedSeance.setJour("JEUDI");

        Map<String, Object> serviceResult = new HashMap<>();
        serviceResult.put("seance", savedSeance);
        serviceResult.put("warnings", Collections.emptyList());

        when(seanceService.save(any(Seance.class), eq(null))).thenReturn(serviceResult);

        String seanceJson = """
            {
                "dateDebut": "2026-05-10T09:00:00",
                "dateFin": "2026-05-10T11:00:00",
                "type": "PRESENTIEL",
                "jour": "JEUDI"
            }
            """;

        mockMvc.perform(post("/api/seances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(seanceJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seance.id").value(2));
    }

    @Test
    void update_returnsOkAndUpdatedSeance() throws Exception {
        Seance updatedSeance = new Seance();
        updatedSeance.setId(1);
        updatedSeance.setJour("VENDREDI");

        Map<String, Object> serviceResult = new HashMap<>();
        serviceResult.put("seance", updatedSeance);
        serviceResult.put("warnings", Collections.emptyList());

        when(seanceService.update(eq(1), any(Seance.class), eq(null))).thenReturn(serviceResult);

        String seanceJson = """
            {
                "dateDebut": "2026-05-11T14:00:00",
                "dateFin": "2026-05-11T16:00:00",
                "type": "EN_LIGNE",
                "jour": "VENDREDI"
            }
            """;

        mockMvc.perform(put("/api/seances/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(seanceJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seance.jour").value("VENDREDI"));
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/seances/5"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getStats_returnsOkWithStats() throws Exception {
        when(seanceService.countClasses()).thenReturn(10L);
        when(seanceService.getAllSalles()).thenReturn(List.of(new HashMap<>(), new HashMap<>()));

        mockMvc.perform(get("/api/seances/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClasses").value(10))
                .andExpect(jsonPath("$.totalSalles").value(2));
    }

    @Test
    void getByClasse_returnsList() throws Exception {
        Seance s = new Seance();
        s.setId(10);
        Classe c = new Classe();
        c.setId(5);
        c.setNom("ClasseA");
        s.setClasse(c);

        when(seanceService.findByClasseId(5)).thenReturn(List.of(s));

        mockMvc.perform(get("/api/seances/classe/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].classeNom").value("ClasseA"));
    }
}
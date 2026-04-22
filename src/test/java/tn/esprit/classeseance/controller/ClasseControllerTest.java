package tn.esprit.classeseance.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.classeseance.entity.Classe;
import tn.esprit.classeseance.service.ClasseService;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClasseController.class)
class ClasseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClasseService classeService;

    @Test
    void getAll_returnsOkAndJsonArray() throws Exception {
        Classe c = new Classe();
        c.setId(1);
        c.setNom("Test");
        when(classeService.findAll()).thenReturn(List.of(c));

        mockMvc.perform(get("/api/classes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nom").value("Test"));
    }

    @Test
    void getById_returns404WhenMissing() throws Exception {
        when(classeService.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/classes/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_returns201() throws Exception {
        Classe saved = new Classe();
        saved.setId(2);
        saved.setNom("Nouvelle");
        when(classeService.save(any(Classe.class))).thenReturn(saved);

        mockMvc.perform(post("/api/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Nouvelle\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void update_returns404WhenServiceThrows() throws Exception {
        when(classeService.update(eq(1), any(Classe.class)))
                .thenThrow(new RuntimeException("Classe non trouvée avec l'id : 1"));

        mockMvc.perform(put("/api/classes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/classes/5"))
                .andExpect(status().isNoContent());
    }
}

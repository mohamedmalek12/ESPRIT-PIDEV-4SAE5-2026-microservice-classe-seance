package tn.esprit.classeseance.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tn.esprit.classeseance.repository.SeanceRepository;
import tn.esprit.classeseance.service.SeanceService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeanceControllerTest {

    @Mock
    private SeanceService seanceService;

    @Mock
    private SeanceRepository seanceRepository;

    @InjectMocks
    private SeanceController seanceController;

    @Test
    void testGetSalles_Success() {
        // Arrange
        List<Map<String, Object>> mockSalles = List.of(Map.of("id", 1, "nom", "Salle A"));
        when(seanceService.getAllSalles()).thenReturn(mockSalles);

        // Act
        ResponseEntity<?> response = seanceController.getSalles();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockSalles, response.getBody());
    }

    @Test
    void testGetSalles_Exception() {
        // Arrange
        when(seanceService.getAllSalles()).thenThrow(new RuntimeException("Erreur de connexion"));

        // Act
        ResponseEntity<?> response = seanceController.getSalles();

        // Assert
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("Service salles-materiels indisponible : Erreur de connexion", response.getBody());
    }

    @Test
    void testGetOccupiedSalles_WithExcludeId() {
        LocalDateTime debut = LocalDateTime.now();
        LocalDateTime fin = LocalDateTime.now().plusHours(2);
        List<Integer> mockIds = List.of(101, 102);
        when(seanceRepository.findOccupiedSalleIdsExcludingId(debut, fin, 5)).thenReturn(mockIds);

        ResponseEntity<List<Integer>> response = seanceController.getOccupiedSalles(debut, fin, 5);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockIds, response.getBody());
    }

    @Test
    void testGetOccupiedSalles_WithoutExcludeId() {
        LocalDateTime debut = LocalDateTime.now();
        LocalDateTime fin = LocalDateTime.now().plusHours(2);
        List<Integer> mockIds = List.of(101, 102);
        when(seanceRepository.findOccupiedSalleIds(debut, fin)).thenReturn(mockIds);

        ResponseEntity<List<Integer>> response = seanceController.getOccupiedSalles(debut, fin, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockIds, response.getBody());
    }

    @Test
    void testGetOccupiedClasses_WithExcludeId() {
        LocalDateTime debut = LocalDateTime.now();
        LocalDateTime fin = LocalDateTime.now().plusHours(2);
        List<Integer> mockIds = List.of(1, 2);
        when(seanceRepository.findOccupiedClasseIdsExcludingId(debut, fin, 10)).thenReturn(mockIds);

        ResponseEntity<List<Integer>> response = seanceController.getOccupiedClasses(debut, fin, 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockIds, response.getBody());
    }

    @Test
    void testGetOccupiedClasses_WithoutExcludeId() {
        LocalDateTime debut = LocalDateTime.now();
        LocalDateTime fin = LocalDateTime.now().plusHours(2);
        List<Integer> mockIds = List.of(1, 2);
        when(seanceRepository.findOccupiedClasseIds(debut, fin)).thenReturn(mockIds);

        ResponseEntity<List<Integer>> response = seanceController.getOccupiedClasses(debut, fin, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockIds, response.getBody());
    }
}
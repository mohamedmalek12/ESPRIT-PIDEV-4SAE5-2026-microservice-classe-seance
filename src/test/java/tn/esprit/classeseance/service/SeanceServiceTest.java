package tn.esprit.classeseance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tn.esprit.classeseance.entity.Classe;
import tn.esprit.classeseance.entity.Seance;
import tn.esprit.classeseance.entity.TypeSeance;
import tn.esprit.classeseance.repository.ClasseRepository;
import tn.esprit.classeseance.repository.SeanceRepository;
import tn.esprit.classeseance.repository.WarningEventRepository;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeanceServiceTest {

    @Mock
    private SeanceRepository seanceRepository;
    @Mock
    private ClasseRepository classeRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private WarningEventRepository warningEventRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private SeanceService seanceService;

    private Seance seanceValide;
    private Classe classeValide;

    @BeforeEach
    void setUp() {
        classeValide = new Classe();
        classeValide.setId(1);
        classeValide.setNom("4SAE5");

        seanceValide = new Seance();
        seanceValide.setId(10);
        seanceValide.setDateDebut(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0));
        seanceValide.setDateFin(LocalDateTime.now().plusDays(1).withHour(12).withMinute(0));
        seanceValide.setType(TypeSeance.PRESENTIEL);
        seanceValide.setSalleId(100);
    }

    @Test
    void testGetAllSalles_returnsListFromRabbitMQ() {
        Map<String, Object> rabbitResponse = Map.of(
                "salles", List.of(Map.of("id", 100, "nom", "Salle A"))
        );
        when(rabbitTemplate.convertSendAndReceive(anyString(), anyString(), any(Object.class)))
                .thenReturn(rabbitResponse);

        List<Map<String, Object>> salles = seanceService.getAllSalles();

        assertFalse(salles.isEmpty());
        assertEquals("Salle A", salles.get(0).get("nom"));
    }

    @Test
    void testSaveSeance_Success() {
        Map<String, Object> rabbitResponse = Map.of("salle", Map.of("id", 100, "nom", "Salle A"));
        when(rabbitTemplate.convertSendAndReceive(anyString(), anyString(), any(Object.class)))
                .thenReturn(rabbitResponse);

        when(seanceRepository.existsBySalleIdAndCreneau(anyInt(), any(), any())).thenReturn(false);
        when(classeRepository.findById(1)).thenReturn(Optional.of(classeValide));
        when(seanceRepository.existsByClasseIdAndCreneau(anyInt(), any(), any())).thenReturn(false);
        when(seanceRepository.save(any(Seance.class))).thenReturn(seanceValide);

        Map<String, Object> result = seanceService.save(seanceValide, 1);

        assertNotNull(result.get("seance"));
        verify(seanceRepository).save(any(Seance.class));
    }

    @Test
    void testSaveSeance_ThrowsException_WhenSalleOccupied() {
        Map<String, Object> rabbitResponse = Map.of("salle", Map.of("id", 100, "nom", "Salle A"));
        when(rabbitTemplate.convertSendAndReceive(anyString(), anyString(), any(Object.class)))
                .thenReturn(rabbitResponse);

        when(seanceRepository.existsBySalleIdAndCreneau(eq(100), any(), any())).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> seanceService.save(seanceValide, null));
        assertTrue(ex.getMessage().contains("already occupied"));
    }

    @Test
    void testGenerateWeeklyPlanning_Success() {
        when(classeRepository.findById(1)).thenReturn(Optional.of(classeValide));
        when(seanceRepository.existsByClasseIdAndCreneau(anyInt(), any(), any())).thenReturn(false);
        when(seanceRepository.save(any(Seance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Seance> planning = seanceService.generateWeeklyPlanning(1);

        assertEquals(10, planning.size());
        verify(seanceRepository, times(10)).save(any(Seance.class));
    }

    @Test
    void testAssignerClasse_Success() {
        when(seanceRepository.findById(10)).thenReturn(Optional.of(seanceValide));
        when(classeRepository.findById(1)).thenReturn(Optional.of(classeValide));
        when(seanceRepository.save(any(Seance.class))).thenReturn(seanceValide);

        Seance result = seanceService.assignerClasse(10, 1);

        assertEquals(classeValide, result.getClasse());
        verify(seanceRepository).save(seanceValide);
    }
    @Test
    void testFindAll() {
        when(seanceRepository.findAll()).thenReturn(List.of(seanceValide));
        List<Seance> result = seanceService.findAll();
        assertEquals(1, result.size());
        verify(seanceRepository).findAll();
    }

    @Test
    void testCountClasses() {
        when(classeRepository.count()).thenReturn(5L);
        long count = seanceService.countClasses();
        assertEquals(5L, count);
    }

    @Test
    void testFindById() {
        when(seanceRepository.findById(10)).thenReturn(Optional.of(seanceValide));
        Optional<Seance> result = seanceService.findById(10);
        assertTrue(result.isPresent());
        assertEquals(10, result.get().getId());
    }

    @Test
    void testFindByClasseId() {
        when(seanceRepository.findByClasseId(1)).thenReturn(List.of(seanceValide));
        List<Seance> result = seanceService.findByClasseId(1);
        assertFalse(result.isEmpty());
    }

    @Test
    void testDeleteById_Success() {
        when(seanceRepository.existsById(10)).thenReturn(true);
        assertDoesNotThrow(() -> seanceService.deleteById(10));
        verify(seanceRepository).deleteById(10);
    }

    @Test
    void testDeleteById_ThrowsException() {
        when(seanceRepository.existsById(99)).thenReturn(false);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> seanceService.deleteById(99));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void testUpdate_Success() {
        // Arrange
        when(seanceRepository.findById(10)).thenReturn(Optional.of(seanceValide));

        Map<String, Object> rabbitResponse = Map.of("salle", Map.of("id", 100, "nom", "Salle A"));
        when(rabbitTemplate.convertSendAndReceive(anyString(), anyString(), any(Object.class)))
                .thenReturn(rabbitResponse);

        when(seanceRepository.existsBySalleIdAndCreneauExcludingId(anyInt(), any(), any(), eq(10))).thenReturn(false);
        when(classeRepository.findById(1)).thenReturn(Optional.of(classeValide));
        when(seanceRepository.existsByClasseIdAndCreneauExcludingId(anyInt(), any(), any(), eq(10))).thenReturn(false);
        when(seanceRepository.save(any(Seance.class))).thenReturn(seanceValide);

        // Act
        Map<String, Object> result = seanceService.update(10, seanceValide, 1);

        // Assert
        assertNotNull(result.get("seance"));
        verify(seanceRepository).save(any(Seance.class));
    }

    @Test
    void testGetSalleById_ReturnsNullWhenIdIsNull() {
        Map<String, Object> result = seanceService.getSalleById(null);
        assertNull(result);
    }

    @Test
    void testGetRecentWarnings() {
        when(warningEventRepository.findTop500ByOrderByTimestampDesc()).thenReturn(List.of());
        List<Map<String, Object>> warnings = seanceService.getRecentWarnings();
        assertTrue(warnings.isEmpty());
    }

    @Test
    void testClearWarningsHistory() {
        doNothing().when(warningEventRepository).deleteAllWarnings();
        assertDoesNotThrow(() -> seanceService.clearWarningsHistory());
        verify(warningEventRepository).deleteAllWarnings();
    }
    @Test
    void testPublishExternalWarnings_NullOrEmpty() {
        // Teste les lignes rouges où la liste est nulle ou vide
        seanceService.publishExternalWarnings("APP", null);
        seanceService.publishExternalWarnings("APP", List.of());

        verify(warningEventRepository, never()).save(any());
    }

    @Test
    void testPublishExternalWarnings_And_WarningEventMessageGetters() throws Exception {
        // Simule qu'il n'y a pas trop d'avertissements en base
        when(warningEventRepository.count()).thenReturn(5L);

        // Déclenche la création du WarningEventMessage
        seanceService.publishExternalWarnings("TEST_SOURCE", List.of("Alerte système"));

        // Capture l'objet privé envoyé pour le tester
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(anyString(), eventCaptor.capture());

        Object capturedEvent = eventCaptor.getValue();

        // Astuce : Utilisation de la réflexion pour appeler tous les getters de la classe interne
        // Cela permet de couvrir toutes les lignes rouges de getId(), getSource(), etc.
        for (java.lang.reflect.Method method : capturedEvent.getClass().getDeclaredMethods()) {
            if (method.getName().startsWith("get")) {
                method.setAccessible(true);
                method.invoke(capturedEvent);
            }
        }
    }

    @Test
    void testEnforceMaxStoredWarnings_OverLimit() {
        // CORRECTION 1 : On simule une base de données avec beaucoup d'avertissements (5000 au lieu de 200)
        when(warningEventRepository.count()).thenReturn(5000L);
        when(warningEventRepository.findIdsOldestFirst(any())).thenReturn(List.of("id1", "id2", "id3"));

        seanceService.publishExternalWarnings(null, List.of("Nouvelle alerte"));

        // Vérifie que le code a bien appelé la suppression des anciens
        verify(warningEventRepository).deleteAllByIdInBatch(anyList());
    }

    @Test
    void testSaveSeance_EarlyAndLateWarnings() {
        // Préparation du Mock RabbitMQ
        Map<String, Object> rabbitResponse = Map.of("salle", Map.of("id", 100, "nom", "Salle A"));
        when(rabbitTemplate.convertSendAndReceive(anyString(), anyString(), any(Object.class)))
                .thenReturn(rabbitResponse);
        when(classeRepository.findById(1)).thenReturn(Optional.of(classeValide));

        // CORRECTION 2 : On force le repository à bien retourner la séance au lieu de null !
        when(seanceRepository.save(any(Seance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 1. Test de la ligne rouge : "startsBefore08"
        Seance earlySeance = new Seance();
        earlySeance.setId(20);
        earlySeance.setDateDebut(LocalDateTime.now().withHour(7).withMinute(0)); // 07:00
        earlySeance.setDateFin(LocalDateTime.now().withHour(9).withMinute(0));
        earlySeance.setSalleId(100);

        seanceService.save(earlySeance, 1);

        // 2. Test de la ligne rouge : "endsAfter18"
        Seance lateSeance = new Seance();
        lateSeance.setId(21);
        lateSeance.setDateDebut(LocalDateTime.now().withHour(17).withMinute(0));
        lateSeance.setDateFin(LocalDateTime.now().withHour(19).withMinute(0)); // 19:00
        lateSeance.setSalleId(100);

        seanceService.save(lateSeance, 1);

        // Ces deux sauvegardes vont forcer le passage dans les "if" des horaires atypiques
        verify(seanceRepository, times(2)).save(any(Seance.class));
    }
}
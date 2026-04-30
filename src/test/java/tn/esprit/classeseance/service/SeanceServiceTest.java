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
}
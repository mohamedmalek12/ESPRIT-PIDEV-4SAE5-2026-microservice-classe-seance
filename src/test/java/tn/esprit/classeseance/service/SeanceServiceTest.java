package tn.esprit.classeseance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tn.esprit.classeseance.entity.WarningEventEntity;
import tn.esprit.classeseance.repository.ClasseRepository;
import tn.esprit.classeseance.repository.SeanceRepository;
import tn.esprit.classeseance.repository.WarningEventRepository;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeanceServiceTest {

    private static final String STOMP_TOPIC_WARNINGS = "/topic/warnings";

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

    private SeanceService seanceService;

    @BeforeEach
    void setUp() {
        seanceService = new SeanceService(
                seanceRepository, classeRepository, messagingTemplate, warningEventRepository, rabbitTemplate);
    }

    @Test
    void getRecentWarnings_initiallyEmpty() {
        when(warningEventRepository.findTop500ByOrderByTimestampDesc()).thenReturn(Collections.emptyList());
        assertTrue(seanceService.getRecentWarnings().isEmpty());
    }

    @Test
    void publishExternalWarnings_sendsToStompAndStoresEvent() {
        seanceService.publishExternalWarnings("MATERIEL", List.of("Stock bas"));

        ArgumentCaptor<WarningEventEntity> saveCaptor = ArgumentCaptor.forClass(WarningEventEntity.class);
        verify(warningEventRepository).save(saveCaptor.capture());
        assertEquals("MATERIEL", saveCaptor.getValue().getSource());
        assertEquals(List.of("Stock bas"), saveCaptor.getValue().getMessages());
    }

    @Test
    void publishExternalWarnings_emptyMessages_doesNothing() {
        seanceService.publishExternalWarnings("X", List.of());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void clearWarningsHistory_deletesAllWarnings() {
        seanceService.clearWarningsHistory();
        verify(warningEventRepository).deleteAllWarnings();
    }

    @Test
    void countClasses_delegatesToRepository() {
        org.mockito.Mockito.when(classeRepository.count()).thenReturn(7L);
        assertEquals(7L, seanceService.countClasses());
    }

    @Test
    void findAll_delegatesToRepository() {
        seanceService.findAll();
        verify(seanceRepository).findAll();
    }
}

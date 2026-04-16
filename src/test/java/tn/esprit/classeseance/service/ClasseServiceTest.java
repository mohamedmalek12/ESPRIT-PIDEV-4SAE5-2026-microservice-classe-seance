package tn.esprit.classeseance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.classeseance.entity.Classe;
import tn.esprit.classeseance.repository.ClasseRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClasseServiceTest {

    @Mock
    private ClasseRepository classeRepository;

    private ClasseService classeService;

    @BeforeEach
    void setUp() {
        classeService = new ClasseService(classeRepository);
    }

    @Test
    void findAll_delegatesToRepository() {
        Classe c = new Classe();
        c.setId(1);
        c.setNom("4SAE5");
        when(classeRepository.findAll()).thenReturn(List.of(c));

        assertEquals(1, classeService.findAll().size());
        assertEquals("4SAE5", classeService.findAll().get(0).getNom());
    }

    @Test
    void findById_returnsOptionalFromRepository() {
        Classe c = new Classe();
        c.setId(2);
        when(classeRepository.findById(2)).thenReturn(Optional.of(c));

        assertTrue(classeService.findById(2).isPresent());
    }

    @Test
    void save_delegatesToRepository() {
        Classe input = new Classe();
        input.setNom("A");
        Classe saved = new Classe();
        saved.setId(1);
        saved.setNom("A");
        when(classeRepository.save(any(Classe.class))).thenReturn(saved);

        assertEquals(1, classeService.save(input).getId());
        verify(classeRepository).save(input);
    }

    @Test
    void update_throwsWhenClasseNotFound() {
        when(classeRepository.findById(99)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> classeService.update(99, new Classe()));
        assertTrue(ex.getMessage().contains("non trouvée"));
    }

    @Test
    void deleteById_throwsWhenNotFound() {
        when(classeRepository.existsById(5)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> classeService.deleteById(5));
    }
}

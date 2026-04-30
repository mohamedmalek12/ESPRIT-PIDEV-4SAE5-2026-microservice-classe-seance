package tn.esprit.classeseance.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.classeseance.entity.Classe;
import tn.esprit.classeseance.repository.ClasseRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class ClasseService {

    private final ClasseRepository classeRepository;

    public ClasseService(ClasseRepository classeRepository) {
        this.classeRepository = classeRepository;
    }

    public List<Classe> findAll() {
        return classeRepository.findAll();
    }

    public Optional<Classe> findById(Integer id) {
        return classeRepository.findById(id);
    }

    @Transactional
    public Classe save(Classe classe) {
        return classeRepository.save(classe);
    }

    @Transactional
    public Classe update(Integer id, Classe classe) {
        return classeRepository.findById(id)
                .map(existing -> {
                    existing.setNom(classe.getNom());
                    return classeRepository.save(existing);
                })
                .orElseThrow(() -> new NoSuchElementException("Classe non trouvée avec l'id : " + id));
    }

    @Transactional
    public void deleteById(Integer id) {
        if (!classeRepository.existsById(id)) {
            throw new NoSuchElementException("Classe non trouvée avec l'id : " + id);
        }
        classeRepository.deleteById(id);
    }
}

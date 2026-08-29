package com.sagimo.nezafi.contrat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ContratRepository extends JpaRepository<Contrat, Long> {
    List<Contrat> findByEmplacementId(Long emplacementId);
    List<Contrat> findByLocataireId(Long locataireId);
    List<Contrat> findByStatut(StatutContrat statut);
    List<Contrat> findByStatutAndDateFinBetweenOrderByDateFinAsc(StatutContrat statut, LocalDate start, LocalDate end);
}

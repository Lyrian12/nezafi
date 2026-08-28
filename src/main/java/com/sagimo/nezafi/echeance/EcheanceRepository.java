package com.sagimo.nezafi.echeance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface EcheanceRepository extends JpaRepository<Echeance, Long> {
    List<Echeance> findByContratId(Long contratId);
    List<Echeance> findByStatut(StatutEcheance statut);
    List<Echeance> findByStatutAndDateEcheanceBefore(StatutEcheance statut, LocalDate date);
}

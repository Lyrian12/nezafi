package com.sagimo.nezafi.emplacement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmplacementRepository extends JpaRepository<Emplacement, Long> {
    List<Emplacement> findByStatut(StatutEmplacement statut);
    List<Emplacement> findByNameContainingIgnoreCase(String name);
}

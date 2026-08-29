package com.sagimo.nezafi.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentJointRepository extends JpaRepository<DocumentJoint, Long> {
    List<DocumentJoint> findByNomEntiteAndEntiteIdOrderByAjouteLeDesc(String nomEntite, Long entiteId);

    void deleteByNomEntiteAndEntiteId(String nomEntite, Long entiteId);
}

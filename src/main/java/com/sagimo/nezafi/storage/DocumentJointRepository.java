package com.sagimo.nezafi.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentJointRepository extends JpaRepository<DocumentJoint, Long> {
    List<DocumentJoint> findByNomEntiteAndEntiteIdOrderByAjouteLeDesc(String nomEntite, Long entiteId);

    void deleteByNomEntiteAndEntiteId(String nomEntite, Long entiteId);

    // Variantes filtrées par type de document (cf. DocumentJoint.typeDocument) — passer
    // typeDocument=null fonctionne aussi (Spring Data JPA traduit "= null" en "IS NULL"),
    // équivalent aux deux méthodes ci-dessus pour les entités qui n'ont qu'une seule catégorie
    // de document possible.
    List<DocumentJoint> findByNomEntiteAndEntiteIdAndTypeDocumentOrderByAjouteLeDesc(
            String nomEntite, Long entiteId, String typeDocument);

    void deleteByNomEntiteAndEntiteIdAndTypeDocument(String nomEntite, Long entiteId, String typeDocument);
}

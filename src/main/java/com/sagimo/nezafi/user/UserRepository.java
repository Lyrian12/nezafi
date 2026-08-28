package com.sagimo.nezafi.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailAndRole(String email, Role role);
    Optional<User> findByTelephone(String telephone);
    Optional<User> findByNumeroCNI(String numeroCNI);
    List<User> findByRole(Role role);

    @Query("SELECT u FROM User u WHERE u.role = :role AND ("
            + "LOWER(u.nom) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(u.prenom) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR u.telephone LIKE CONCAT('%', :search, '%'))")
    List<User> searchByRoleAndNomOrPrenomOrTelephone(@Param("role") Role role, @Param("search") String search);
}

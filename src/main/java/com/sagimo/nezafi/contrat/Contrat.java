package com.sagimo.nezafi.contrat;

import com.sagimo.nezafi.boutique.Boutique;
import com.sagimo.nezafi.user.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Contrat {
    private long id;
    private Boutique boutique;
    private User locataire;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private String termes;
    private StatutContrat statut;
}

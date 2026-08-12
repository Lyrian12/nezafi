package com.sagimo.nezafi.contrat;

import com.sagimo.nezafi.boutique.BoutiqueRepository;
import com.sagimo.nezafi.user.UserRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/contrats")
public class ContratController extends ContratRepository{

    public final ContratRepository contratRepository;
    private final BoutiqueRepository boutiqueRepository;
    private final UserRepository userRepository;

    public ContratController(ContratRepository contratRepository, BoutiqueRepository boutiqueRepository,
                             UserRepository userRepository){
        this.contratRepository = contratRepository;
        this.boutiqueRepository = boutiqueRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<Contrat> findBoutiqueId(long boutiqueid) {
        return List.of();
    }
}

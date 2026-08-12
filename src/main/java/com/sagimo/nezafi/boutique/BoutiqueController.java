package com.sagimo.nezafi.boutique;


import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/boutiques")
public class BoutiqueController {


    private final BoutiqueRepository boutiqueRepository;


    public BoutiqueController (BoutiqueRepository boutiqueRepository){
        this.boutiqueRepository = boutiqueRepository;
    }


}

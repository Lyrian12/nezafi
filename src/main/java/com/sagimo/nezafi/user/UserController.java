package com.sagimo.nezafi.user;

import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {


    public UserRepository userRepository;
    public UserController(UserRepository userRepository){
        this.userRepository = userRepository;
    }
}

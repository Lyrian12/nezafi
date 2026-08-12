package com.sagimo.nezafi.user;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@AllArgsConstructor
@NoArgsConstructor
@Data
public class User {

    private long id;
    private String name;
    private int telephone;
    private String email;
    private Role role;

}

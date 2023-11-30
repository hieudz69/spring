package com.example.springsecurity.model.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * @author NamTv
 * @since 12/10/2023
 */
@Getter
@Setter
@NoArgsConstructor
public class JwtResponse {
    private Long id;
    private String type = "Bearer ";
    private String username;
    private String email;
    private List<String> roles;
    private String accessToken;


    public JwtResponse(Long id, String username, String email, List<String> roles, String token) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.roles = roles;
        this.accessToken = token;
    }
}

package com.dsyg.platform.modules;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class RootController {
    private final String frontendUrl;

    public RootController(@Value("${platform.frontend-url:http://localhost:5683/}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @GetMapping("/")
    public ResponseEntity<Void> redirectToFrontend() {
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(frontendUrl))
            .build();
    }
}

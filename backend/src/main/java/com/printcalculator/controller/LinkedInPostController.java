package com.printcalculator.controller;

import com.printcalculator.dto.LinkedInPostDto;
import com.printcalculator.service.LinkedInPostService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/public/linkedin")
public class LinkedInPostController {

    private final LinkedInPostService linkedInPostService;

    public LinkedInPostController(LinkedInPostService linkedInPostService) {
        this.linkedInPostService = linkedInPostService;
    }

    @GetMapping("/posts")
    public ResponseEntity<List<LinkedInPostDto>> getPosts() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(linkedInPostService.getPosts());
    }
}

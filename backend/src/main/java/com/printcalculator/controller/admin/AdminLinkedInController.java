package com.printcalculator.controller.admin;

import com.printcalculator.dto.LinkedInSyncStatusDto;
import com.printcalculator.service.LinkedInPostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/linkedin")
public class AdminLinkedInController {

    private final LinkedInPostService linkedInPostService;

    public AdminLinkedInController(LinkedInPostService linkedInPostService) {
        this.linkedInPostService = linkedInPostService;
    }

    @GetMapping("/status")
    public ResponseEntity<LinkedInSyncStatusDto> getStatus() {
        return ResponseEntity.ok(linkedInPostService.getSyncStatus());
    }

    @PostMapping("/refresh")
    public ResponseEntity<LinkedInSyncStatusDto> refresh() {
        return ResponseEntity.ok(linkedInPostService.refreshPosts());
    }
}

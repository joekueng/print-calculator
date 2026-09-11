package com.printcalculator.controller.admin;

import com.printcalculator.dto.AdminCadInvoiceCreateRequest;
import com.printcalculator.dto.AdminCadInvoiceDto;
import com.printcalculator.dto.AdminContactRequestDetailDto;
import com.printcalculator.dto.AdminContactRequestDto;
import com.printcalculator.dto.AdminFilamentStockDto;
import com.printcalculator.dto.AdminQuoteSessionDto;
import com.printcalculator.dto.AdminSessionStatisticsDto;
import com.printcalculator.dto.AdminUpdateContactRequestStatusRequest;
import com.printcalculator.service.admin.AdminOperationsControllerService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@Transactional(readOnly = true)
public class AdminOperationsController {

    private final AdminOperationsControllerService adminOperationsControllerService;

    public AdminOperationsController(AdminOperationsControllerService adminOperationsControllerService) {
        this.adminOperationsControllerService = adminOperationsControllerService;
    }

    @GetMapping("/filament-stock")
    public ResponseEntity<List<AdminFilamentStockDto>> getFilamentStock() {
        return ResponseEntity.ok(adminOperationsControllerService.getFilamentStock());
    }

    @GetMapping("/contact-requests")
    public ResponseEntity<List<AdminContactRequestDto>> getContactRequests() {
        return ResponseEntity.ok(adminOperationsControllerService.getContactRequests());
    }

    @GetMapping("/contact-requests/{requestId}")
    public ResponseEntity<AdminContactRequestDetailDto> getContactRequestDetail(@PathVariable UUID requestId) {
        return ResponseEntity.ok(adminOperationsControllerService.getContactRequestDetail(requestId));
    }

    @PatchMapping("/contact-requests/{requestId}/status")
    @Transactional
    public ResponseEntity<AdminContactRequestDetailDto> updateContactRequestStatus(
            @PathVariable UUID requestId,
            @RequestBody AdminUpdateContactRequestStatusRequest payload
    ) {
        return ResponseEntity.ok(adminOperationsControllerService.updateContactRequestStatus(requestId, payload));
    }

    @PostMapping("/contact-requests/{requestId}/email-logs/{emailLogId}/resend")
    @Transactional
    public ResponseEntity<AdminContactRequestDetailDto> resendContactRequestEmail(
            @PathVariable UUID requestId,
            @PathVariable UUID emailLogId
    ) {
        return ResponseEntity.ok(adminOperationsControllerService.resendContactRequestEmail(requestId, emailLogId));
    }

    @GetMapping("/contact-requests/{requestId}/attachments/{attachmentId}/file")
    public ResponseEntity<Resource> downloadContactRequestAttachment(
            @PathVariable UUID requestId,
            @PathVariable UUID attachmentId
    ) {
        return adminOperationsControllerService.downloadContactRequestAttachment(requestId, attachmentId);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<AdminQuoteSessionDto>> getQuoteSessions() {
        return ResponseEntity.ok(adminOperationsControllerService.getQuoteSessions());
    }

    @GetMapping("/sessions/statistics")
    public ResponseEntity<AdminSessionStatisticsDto> getSessionStatistics() {
        return ResponseEntity.ok(adminOperationsControllerService.getSessionStatistics());
    }

    @GetMapping("/cad-invoices")
    public ResponseEntity<List<AdminCadInvoiceDto>> getCadInvoices() {
        return ResponseEntity.ok(adminOperationsControllerService.getCadInvoices());
    }

    @PostMapping("/cad-invoices")
    @Transactional
    public ResponseEntity<AdminCadInvoiceDto> createOrUpdateCadInvoice(
            @RequestBody AdminCadInvoiceCreateRequest payload
    ) {
        return ResponseEntity.ok(adminOperationsControllerService.createOrUpdateCadInvoice(payload));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Transactional
    public ResponseEntity<Void> deleteQuoteSession(@PathVariable UUID sessionId) {
        adminOperationsControllerService.deleteQuoteSession(sessionId);
        return ResponseEntity.noContent().build();
    }
}

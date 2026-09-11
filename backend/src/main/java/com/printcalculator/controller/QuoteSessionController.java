package com.printcalculator.controller;

import com.printcalculator.dto.PrintSettingsDto;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.QuoteCalculator;
import com.printcalculator.service.QuoteRateLimitService;
import com.printcalculator.service.QuoteSessionExpiryPolicy;
import com.printcalculator.service.QuoteSessionTotalsService;
import com.printcalculator.service.quote.QuoteSessionItemService;
import com.printcalculator.service.quote.QuoteSessionResponseAssembler;
import com.printcalculator.service.quote.QuoteStorageService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/quote-sessions")
public class QuoteSessionController {
    private final QuoteSessionRepository sessionRepo;
    private final QuoteLineItemRepository lineItemRepo;
    private final QuoteCalculator quoteCalculator;
    private final com.printcalculator.repository.PricingPolicyRepository pricingRepo;
    private final QuoteSessionTotalsService quoteSessionTotalsService;
    private final QuoteSessionItemService quoteSessionItemService;
    private final QuoteStorageService quoteStorageService;
    private final QuoteSessionResponseAssembler quoteSessionResponseAssembler;
    private final QuoteSessionExpiryPolicy quoteSessionExpiryPolicy;
    private final QuoteRateLimitService quoteRateLimitService;

    public QuoteSessionController(QuoteSessionRepository sessionRepo,
                                  QuoteLineItemRepository lineItemRepo,
                                  QuoteCalculator quoteCalculator,
                                  com.printcalculator.repository.PricingPolicyRepository pricingRepo,
                                  QuoteSessionTotalsService quoteSessionTotalsService,
                                  QuoteSessionItemService quoteSessionItemService,
                                  QuoteStorageService quoteStorageService,
                                  QuoteSessionResponseAssembler quoteSessionResponseAssembler,
                                  QuoteSessionExpiryPolicy quoteSessionExpiryPolicy,
                                  QuoteRateLimitService quoteRateLimitService) {
        this.sessionRepo = sessionRepo;
        this.lineItemRepo = lineItemRepo;
        this.quoteCalculator = quoteCalculator;
        this.pricingRepo = pricingRepo;
        this.quoteSessionTotalsService = quoteSessionTotalsService;
        this.quoteSessionItemService = quoteSessionItemService;
        this.quoteStorageService = quoteStorageService;
        this.quoteSessionResponseAssembler = quoteSessionResponseAssembler;
        this.quoteSessionExpiryPolicy = quoteSessionExpiryPolicy;
        this.quoteRateLimitService = quoteRateLimitService;
    }

    @PostMapping(value = "")
    @Transactional
    public ResponseEntity<QuoteSession> createSession(HttpServletRequest request) {
        quoteRateLimitService.checkAllowed(request);
        QuoteSession session = new QuoteSession();
        session.setStatus("ACTIVE");
        session.setSessionType("PRINT_QUOTE");
        session.setPricingVersion("v1");
        session.setMaterialCode("PLA");
        session.setSupportsEnabled(false);
        session.setCreatedAt(OffsetDateTime.now());
        session.setExpiresAt(quoteSessionExpiryPolicy.newExpiry());

        var policy = pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc();
        session.setSetupCostChf(quoteCalculator.calculateSessionSetupFee(policy));

        session = sessionRepo.save(session);
        return ResponseEntity.ok(session);
    }

    @PostMapping(value = "/{id}/line-items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<QuoteLineItem> addItemToExistingSession(@PathVariable UUID id,
                                                                   @RequestPart("settings") PrintSettingsDto settings,
                                                                   @RequestPart("file") MultipartFile file,
                                                                   HttpServletRequest request) throws IOException {
        quoteRateLimitService.checkAllowed(request);
        QuoteSession session = sessionRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        QuoteLineItem item = quoteSessionItemService.addItemToSession(session, file, settings);
        return ResponseEntity.ok(item);
    }

    @PatchMapping("/line-items/{lineItemId}")
    @Transactional
    public ResponseEntity<QuoteLineItem> updateLineItem(@PathVariable UUID lineItemId,
                                                         @RequestBody Map<String, Object> updates) {
        QuoteLineItem item = lineItemRepo.findById(lineItemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));

        QuoteSession session = item.getQuoteSession();
        if ("CONVERTED".equals(session.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Cannot modify a converted session");
        }

        if (updates.containsKey("quantity")) {
            item.setQuantity(parsePositiveQuantity(updates.get("quantity")));
        }
        if (updates.containsKey("color_code")) {
            Object colorValue = updates.get("color_code");
            if (colorValue != null) {
                item.setColorCode(String.valueOf(colorValue));
            }
        }

        item.setUpdatedAt(OffsetDateTime.now());
        return ResponseEntity.ok(lineItemRepo.save(item));
    }

    @DeleteMapping("/{sessionId}/line-items/{lineItemId}")
    @Transactional
    public ResponseEntity<Void> deleteLineItem(@PathVariable UUID sessionId,
                                               @PathVariable UUID lineItemId) {
        QuoteLineItem item = lineItemRepo.findById(lineItemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));

        if (!item.getQuoteSession().getId().equals(sessionId)) {
            return ResponseEntity.badRequest().build();
        }

        lineItemRepo.delete(item);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getQuoteSession(@PathVariable UUID id) {
        QuoteSession session = sessionRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        List<QuoteLineItem> items = lineItemRepo.findByQuoteSessionIdOrderByCreatedAtAsc(id);
        QuoteSessionTotalsService.QuoteSessionTotals totals = quoteSessionTotalsService.compute(session, items);
        return ResponseEntity.ok(quoteSessionResponseAssembler.assemble(session, items, totals));
    }

    @GetMapping(value = "/{sessionId}/line-items/{lineItemId}/content")
    public ResponseEntity<Resource> downloadLineItemContent(@PathVariable UUID sessionId,
                                                            @PathVariable UUID lineItemId,
                                                            @RequestParam(name = "preview", required = false, defaultValue = "false") boolean preview)
            throws IOException {
        QuoteLineItem item = lineItemRepo.findById(lineItemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));

        if (!item.getQuoteSession().getId().equals(sessionId)) {
            return ResponseEntity.badRequest().build();
        }

        String targetStoredPath = item.getStoredPath();
        if (preview) {
            String convertedPath = quoteStorageService.extractConvertedStoredPath(item);
            if (convertedPath != null && !convertedPath.isBlank()) {
                targetStoredPath = convertedPath;
            }
        }

        if (targetStoredPath == null) {
            return ResponseEntity.notFound().build();
        }

        java.nio.file.Path path = quoteStorageService.resolveStoredQuotePath(targetStoredPath, sessionId);
        if (path == null || !java.nio.file.Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(path.toUri());
        String downloadName = preview ? path.getFileName().toString() : item.getOriginalFilename();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
                .body(resource);
    }

    @GetMapping(value = "/{sessionId}/line-items/{lineItemId}/stl-preview")
    public ResponseEntity<Resource> downloadLineItemStlPreview(@PathVariable UUID sessionId,
                                                               @PathVariable UUID lineItemId)
            throws IOException {
        QuoteLineItem item = lineItemRepo.findById(lineItemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));

        if (!item.getQuoteSession().getId().equals(sessionId)) {
            return ResponseEntity.badRequest().build();
        }

        if (!"stl".equals(quoteStorageService.getSafeExtension(item.getOriginalFilename(), ""))) {
            return ResponseEntity.notFound().build();
        }

        String targetStoredPath = item.getStoredPath();
        if (targetStoredPath == null || targetStoredPath.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        java.nio.file.Path path = quoteStorageService.resolveStoredQuotePath(targetStoredPath, sessionId);
        if (path == null || !java.nio.file.Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        if (!"stl".equals(quoteStorageService.getSafeExtension(path.getFileName().toString(), ""))) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(path.toUri());
        String downloadName = path.getFileName().toString();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("model/stl"))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + downloadName + "\"")
                .body(resource);
    }

    private int parsePositiveQuantity(Object raw) {
        if (raw == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Quantity is required");
        }

        int quantity;
        if (raw instanceof Number number) {
            double numericValue = number.doubleValue();
            if (!Double.isFinite(numericValue)) {
                throw new ResponseStatusException(BAD_REQUEST, "Quantity must be a finite number");
            }
            quantity = (int) Math.floor(numericValue);
        } else {
            try {
                quantity = Integer.parseInt(String.valueOf(raw).trim());
            } catch (NumberFormatException ex) {
                throw new ResponseStatusException(BAD_REQUEST, "Quantity must be an integer");
            }
        }

        if (quantity < 1) {
            throw new ResponseStatusException(BAD_REQUEST, "Quantity must be >= 1");
        }
        return quantity;
    }
}

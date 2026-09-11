package com.printcalculator.controller;

import com.printcalculator.dto.CadCheckoutItemRequest;
import com.printcalculator.service.quote.CadCheckoutService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/quote-sessions/{sessionId}/cad-items")
public class CadCheckoutController {
    private final CadCheckoutService service;
    public CadCheckoutController(CadCheckoutService service) { this.service = service; }

    @PatchMapping("/{itemId}")
    public Map<String, Object> update(@PathVariable UUID sessionId, @PathVariable UUID itemId,
                                     @Valid @RequestBody CadCheckoutItemRequest request) {
        return service.update(sessionId, itemId, request);
    }
}

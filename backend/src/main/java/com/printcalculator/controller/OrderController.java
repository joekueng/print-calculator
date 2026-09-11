package com.printcalculator.controller;

import com.printcalculator.dto.CreateOrderRequest;
import com.printcalculator.dto.OrderDto;
import com.printcalculator.service.order.OrderControllerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderControllerService orderControllerService;

    public OrderController(OrderControllerService orderControllerService) {
        this.orderControllerService = orderControllerService;
    }

    @PostMapping("/from-quote/{quoteSessionId}")
    @Transactional
    public ResponseEntity<OrderDto> createOrderFromQuote(
            @PathVariable UUID quoteSessionId,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        return ResponseEntity.ok(orderControllerService.createOrderFromQuote(quoteSessionId, request));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDto> getOrder(@PathVariable UUID orderId) {
        return orderControllerService.getOrder(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{orderId}/payments/report")
    @Transactional
    public ResponseEntity<OrderDto> reportPayment(
            @PathVariable UUID orderId,
            @RequestBody Map<String, String> payload
    ) {
        return orderControllerService.reportPayment(orderId, payload.get("method"))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{orderId}/confirmation")
    public ResponseEntity<byte[]> getConfirmation(@PathVariable UUID orderId) {
        return orderControllerService.getConfirmation(orderId);
    }

    @GetMapping("/{orderId}/cad-files/download")
    public ResponseEntity<StreamingResponseBody> downloadCadFiles(@PathVariable UUID orderId) {
        ResponseEntity<?> response = orderControllerService.downloadCadFiles(orderId);
        Object body = response.getBody();
        StreamingResponseBody stream;
        if (body instanceof StreamingResponseBody streaming) {
            stream = streaming;
        } else if (body instanceof Resource resource) {
            stream = output -> {
                try (var input = resource.getInputStream()) { input.transferTo(output); }
            };
        } else {
            throw new IllegalStateException("Unsupported CAD download response");
        }
        return new ResponseEntity<>(stream, response.getHeaders(), response.getStatusCode());
    }

    @GetMapping("/{orderId}/invoice")
    public ResponseEntity<byte[]> getInvoice(@PathVariable UUID orderId) {
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{orderId}/twint")
    public ResponseEntity<Map<String, String>> getTwintPayment(@PathVariable UUID orderId) {
        return orderControllerService.getTwintPayment(orderId);
    }

    @GetMapping("/{orderId}/twint/open")
    public ResponseEntity<Void> openTwintPayment(@PathVariable UUID orderId) {
        return orderControllerService.openTwintPayment(orderId);
    }

    @GetMapping("/{orderId}/twint/qr")
    public ResponseEntity<byte[]> getTwintQr(
            @PathVariable UUID orderId,
            @RequestParam(defaultValue = "320") int size
    ) {
        return orderControllerService.getTwintQr(orderId, size);
    }
}

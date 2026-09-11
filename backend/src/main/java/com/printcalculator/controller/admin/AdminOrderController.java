package com.printcalculator.controller.admin;

import com.printcalculator.dto.AdminOrderStatusUpdateRequest;
import com.printcalculator.dto.AdminOrderStatisticsDto;
import com.printcalculator.dto.OrderDto;
import com.printcalculator.service.order.AdminOrderControllerService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/orders")
@Transactional(readOnly = true)
public class AdminOrderController {

    private final AdminOrderControllerService adminOrderControllerService;

    public AdminOrderController(AdminOrderControllerService adminOrderControllerService) {
        this.adminOrderControllerService = adminOrderControllerService;
    }

    @GetMapping
    public ResponseEntity<List<OrderDto>> listOrders() {
        return ResponseEntity.ok(adminOrderControllerService.listOrders());
    }

    @GetMapping("/statistics")
    public ResponseEntity<AdminOrderStatisticsDto> getStatistics() {
        return ResponseEntity.ok(adminOrderControllerService.getStatistics());
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDto> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(adminOrderControllerService.getOrder(orderId));
    }

    @PatchMapping("/{orderId}/payments/method")
    @Transactional
    public ResponseEntity<OrderDto> updatePaymentMethod(
            @PathVariable UUID orderId,
            @RequestBody(required = false) Map<String, String> payload
    ) {
        return ResponseEntity.ok(adminOrderControllerService.updatePaymentMethod(orderId, payload));
    }

    @PostMapping("/{orderId}/status")
    @Transactional
    public ResponseEntity<OrderDto> updateOrderStatus(
            @PathVariable UUID orderId,
            @RequestBody AdminOrderStatusUpdateRequest payload
    ) {
        return ResponseEntity.ok(adminOrderControllerService.updateOrderStatus(orderId, payload));
    }

    @PostMapping("/{orderId}/email-logs/{emailLogId}/resend")
    @Transactional
    public ResponseEntity<OrderDto> resendEmail(
            @PathVariable UUID orderId,
            @PathVariable UUID emailLogId
    ) {
        return ResponseEntity.ok(adminOrderControllerService.resendEmail(orderId, emailLogId));
    }

    @GetMapping("/{orderId}/items/{orderItemId}/file")
    public ResponseEntity<Resource> downloadOrderItemFile(
            @PathVariable UUID orderId,
            @PathVariable UUID orderItemId
    ) {
        return adminOrderControllerService.downloadOrderItemFile(orderId, orderItemId);
    }

    @GetMapping("/{orderId}/documents/confirmation")
    public ResponseEntity<byte[]> downloadOrderConfirmation(@PathVariable UUID orderId) {
        return adminOrderControllerService.downloadOrderConfirmation(orderId);
    }

    @GetMapping("/{orderId}/documents/invoice")
    public ResponseEntity<byte[]> downloadOrderInvoice(@PathVariable UUID orderId) {
        return adminOrderControllerService.downloadOrderInvoice(orderId);
    }

    @PostMapping(value = "/{orderId}/cad-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<OrderDto> uploadCadFiles(
            @PathVariable UUID orderId,
            @RequestParam("files") List<MultipartFile> files
    ) {
        return ResponseEntity.ok(adminOrderControllerService.uploadCadFiles(orderId, files));
    }

    @DeleteMapping("/{orderId}/cad-files/{fileId}")
    @Transactional
    public ResponseEntity<OrderDto> deleteCadFile(
            @PathVariable UUID orderId,
            @PathVariable UUID fileId
    ) {
        return ResponseEntity.ok(adminOrderControllerService.deleteCadFile(orderId, fileId));
    }
}

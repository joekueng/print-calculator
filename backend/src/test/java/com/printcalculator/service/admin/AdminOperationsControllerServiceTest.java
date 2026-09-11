package com.printcalculator.service.admin;

import com.printcalculator.dto.AdminCadInvoiceCreateRequest;
import com.printcalculator.dto.AdminCadInvoiceDto;
import com.printcalculator.dto.AdminContactRequestDetailDto;
import com.printcalculator.dto.AdminSessionStatisticsDto;
import com.printcalculator.dto.AdminUpdateContactRequestStatusRequest;
import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.entity.CustomQuoteRequestAttachment;
import com.printcalculator.entity.EmailLog;
import com.printcalculator.entity.PricingPolicy;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.CustomQuoteRequestAttachmentRepository;
import com.printcalculator.repository.CustomQuoteRequestRepository;
import com.printcalculator.repository.EmailLogRepository;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.FilamentVariantStockKgRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.PricingPolicyRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.QuoteSessionExpiryPolicy;
import com.printcalculator.service.QuoteSessionTotalsService;
import com.printcalculator.service.email.EmailAuditService;
import com.printcalculator.service.request.CustomQuoteRequestNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOperationsControllerServiceTest {

    @Mock
    private FilamentVariantStockKgRepository filamentStockRepo;
    @Mock
    private FilamentVariantRepository filamentVariantRepo;
    @Mock
    private CustomQuoteRequestRepository customQuoteRequestRepo;
    @Mock
    private CustomQuoteRequestAttachmentRepository customQuoteRequestAttachmentRepo;
    @Mock
    private EmailLogRepository emailLogRepo;
    @Mock
    private QuoteSessionRepository quoteSessionRepo;
    @Mock
    private QuoteLineItemRepository quoteLineItemRepo;
    @Mock
    private OrderRepository orderRepo;
    @Mock
    private PricingPolicyRepository pricingRepo;
    @Mock
    private QuoteSessionTotalsService quoteSessionTotalsService;
    @Mock
    private QuoteSessionExpiryPolicy quoteSessionExpiryPolicy;
    @Mock
    private EmailAuditService emailAuditService;
    @Mock
    private CustomQuoteRequestNotificationService contactRequestNotificationService;

    @InjectMocks
    private AdminOperationsControllerService service;

    @Test
    void getSessionStatistics_shouldCalculateRatiosFromReadOnlyAggregates() {
        when(quoteSessionRepo.countAllForStatistics()).thenReturn(6L);
        when(quoteSessionRepo.countWithItemsForStatistics()).thenReturn(4L);
        when(quoteSessionRepo.countLineItemsForStatistics()).thenReturn(7L);
        when(quoteSessionRepo.countConvertedForStatistics()).thenReturn(3L);
        when(quoteSessionRepo.countPaidConvertedForStatistics()).thenReturn(2L);
        when(quoteSessionRepo.countModifiedForStatistics()).thenReturn(1L);
        when(quoteSessionRepo.countExpiredWithoutPaidConversionForStatistics(any(OffsetDateTime.class)))
                .thenReturn(2L);

        AdminSessionStatisticsDto dto = service.getSessionStatistics();

        assertEquals(6L, dto.getTotalSessionCount());
        assertEquals(4L, dto.getSessionsWithItemsCount());
        assertEquals(2L, dto.getEmptySessionCount());
        assertEquals(3L, dto.getConvertedSessionCount());
        assertEquals(2L, dto.getPaidConvertedSessionCount());
        assertEquals(new BigDecimal("1.75"), dto.getAverageItemsPerActiveSession());
        assertEquals(new BigDecimal("50.00"), dto.getPaidConversionRatePercent());
        assertEquals(1L, dto.getModifiedSessionCount());
        assertEquals(2L, dto.getExpiredAbandonedSessionCount());
    }

    @Test
    void updateContactRequestStatus_withInvalidStatus_shouldReturnBadRequest() {
        UUID requestId = UUID.randomUUID();
        CustomQuoteRequest request = new CustomQuoteRequest();
        request.setId(requestId);
        request.setStatus("PENDING");
        when(customQuoteRequestRepo.findById(requestId)).thenReturn(Optional.of(request));

        AdminUpdateContactRequestStatusRequest payload = new AdminUpdateContactRequestStatusRequest();
        payload.setStatus("wrong");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.updateContactRequestStatus(requestId, payload)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(customQuoteRequestRepo, never()).save(any(CustomQuoteRequest.class));
    }

    @Test
    void updateContactRequestStatus_withValidStatus_shouldPersistAndReturnDetail() {
        UUID requestId = UUID.randomUUID();
        CustomQuoteRequest request = new CustomQuoteRequest();
        request.setId(requestId);
        request.setStatus("PENDING");
        request.setCreatedAt(OffsetDateTime.now());
        request.setUpdatedAt(OffsetDateTime.now());

        CustomQuoteRequestAttachment attachment = new CustomQuoteRequestAttachment();
        attachment.setId(UUID.randomUUID());
        attachment.setOriginalFilename("drawing.stp");
        attachment.setMimeType("application/step");
        attachment.setFileSizeBytes(123L);
        attachment.setCreatedAt(OffsetDateTime.now());

        when(customQuoteRequestRepo.findById(requestId)).thenReturn(Optional.of(request));
        when(customQuoteRequestRepo.save(any(CustomQuoteRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(customQuoteRequestAttachmentRepo.findByRequest_IdOrderByCreatedAtAsc(requestId)).thenReturn(List.of(attachment));

        AdminUpdateContactRequestStatusRequest payload = new AdminUpdateContactRequestStatusRequest();
        payload.setStatus("done");

        AdminContactRequestDetailDto dto = service.updateContactRequestStatus(requestId, payload);

        assertEquals("DONE", dto.getStatus());
        assertNotNull(dto.getUpdatedAt());
        assertEquals(1, dto.getAttachments().size());
        verify(customQuoteRequestRepo).save(request);
    }

    @Test
    void resendContactRequestEmail_withRequestEmailLog_shouldDelegateAndReturnDetail() {
        UUID requestId = UUID.randomUUID();
        UUID emailLogId = UUID.randomUUID();
        CustomQuoteRequest request = new CustomQuoteRequest();
        request.setId(requestId);
        request.setStatus("PENDING");
        request.setCreatedAt(OffsetDateTime.now());
        request.setUpdatedAt(OffsetDateTime.now());

        EmailLog emailLog = new EmailLog();
        emailLog.setId(emailLogId);
        emailLog.setContactRequest(request);
        emailLog.setEventType("CONTACT_REQUEST_CUSTOMER");

        when(customQuoteRequestRepo.findById(requestId)).thenReturn(Optional.of(request));
        when(emailLogRepo.findById(emailLogId)).thenReturn(Optional.of(emailLog));
        when(customQuoteRequestAttachmentRepo.findByRequest_IdOrderByCreatedAtAsc(requestId)).thenReturn(List.of());

        AdminContactRequestDetailDto dto = service.resendContactRequestEmail(requestId, emailLogId);

        assertEquals(requestId, dto.getId());
        verify(contactRequestNotificationService).resendNotification(request, 0, emailLog);
    }

    @Test
    void createOrUpdateCadInvoice_withMissingCadHours_shouldReturnBadRequest() {
        AdminCadInvoiceCreateRequest payload = new AdminCadInvoiceCreateRequest();

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createOrUpdateCadInvoice(payload)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createOrUpdateCadInvoice_withConvertedSession_shouldReturnConflict() {
        UUID sessionId = UUID.randomUUID();
        QuoteSession session = new QuoteSession();
        session.setId(sessionId);
        session.setStatus("CONVERTED");

        when(quoteSessionRepo.findById(sessionId)).thenReturn(Optional.of(session));

        AdminCadInvoiceCreateRequest payload = new AdminCadInvoiceCreateRequest();
        payload.setSessionId(sessionId);
        payload.setCadHours(new BigDecimal("1.0"));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createOrUpdateCadInvoice(payload)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void createOrUpdateCadInvoice_withNewSession_shouldUsePolicyCadRate() {
        PricingPolicy policy = new PricingPolicy();
        policy.setCadCostChfPerHour(new BigDecimal("85"));

        when(pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc()).thenReturn(policy);
        when(quoteSessionExpiryPolicy.newExpiry()).thenReturn(OffsetDateTime.now().plusMonths(6));
        when(quoteSessionRepo.save(any(QuoteSession.class))).thenAnswer(invocation -> {
            QuoteSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(UUID.randomUUID());
            }
            return session;
        });
        when(quoteLineItemRepo.findByQuoteSessionId(any(UUID.class))).thenReturn(List.of());
        when(quoteSessionTotalsService.compute(any(QuoteSession.class), anyList()))
                .thenReturn(new QuoteSessionTotalsService.QuoteSessionTotals(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("212.50"),
                        new BigDecimal("212.50"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("212.50"),
                        BigDecimal.ZERO
                ));

        AdminCadInvoiceCreateRequest payload = new AdminCadInvoiceCreateRequest();
        payload.setCadHours(new BigDecimal("2.5"));
        payload.setCadHourlyRateChf(null);
        payload.setNotes("  Custom CAD work  ");

        AdminCadInvoiceDto dto = service.createOrUpdateCadInvoice(payload);

        assertEquals("CAD_ACTIVE", dto.getSessionStatus());
        assertEquals(new BigDecimal("2.50"), dto.getCadHours());
        assertEquals(new BigDecimal("85.00"), dto.getCadHourlyRateChf());
        assertEquals("Custom CAD work", dto.getNotes());
        assertEquals(new BigDecimal("212.50"), dto.getCadTotalChf());
    }

    @Test
    void deleteQuoteSession_whenLinkedToOrder_shouldReturnConflict() {
        UUID sessionId = UUID.randomUUID();
        QuoteSession session = new QuoteSession();
        session.setId(sessionId);

        when(quoteSessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(orderRepo.existsBySourceQuoteSession_Id(sessionId)).thenReturn(true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.deleteQuoteSession(sessionId)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(quoteSessionRepo, never()).delete(any(QuoteSession.class));
    }
}

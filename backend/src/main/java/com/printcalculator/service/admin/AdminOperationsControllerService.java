package com.printcalculator.service.admin;

import com.printcalculator.dto.AdminCadInvoiceCreateRequest;
import com.printcalculator.dto.AdminCadInvoiceDto;
import com.printcalculator.dto.AdminContactRequestAttachmentDto;
import com.printcalculator.dto.AdminContactRequestDetailDto;
import com.printcalculator.dto.AdminContactRequestDto;
import com.printcalculator.dto.AdminFilamentStockDto;
import com.printcalculator.dto.AdminQuoteSessionDto;
import com.printcalculator.dto.AdminSessionStatisticsDto;
import com.printcalculator.dto.AdminUpdateContactRequestStatusRequest;
import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.entity.CustomQuoteRequestAttachment;
import com.printcalculator.entity.EmailLog;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.FilamentVariantStockKg;
import com.printcalculator.entity.Order;
import com.printcalculator.entity.QuoteLineItem;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional(readOnly = true)
public class AdminOperationsControllerService {
    private static final Logger logger = LoggerFactory.getLogger(AdminOperationsControllerService.class);
    private static final Path CONTACT_ATTACHMENTS_ROOT = Paths.get("storage_requests").toAbsolutePath().normalize();
    private static final Set<String> CONTACT_REQUEST_ALLOWED_STATUSES = Set.of(
            "NEW", "PENDING", "IN_PROGRESS", "DONE", "CLOSED"
    );

    private final FilamentVariantStockKgRepository filamentStockRepo;
    private final FilamentVariantRepository filamentVariantRepo;
    private final CustomQuoteRequestRepository customQuoteRequestRepo;
    private final CustomQuoteRequestAttachmentRepository customQuoteRequestAttachmentRepo;
    private final EmailLogRepository emailLogRepo;
    private final QuoteSessionRepository quoteSessionRepo;
    private final QuoteLineItemRepository quoteLineItemRepo;
    private final OrderRepository orderRepo;
    private final PricingPolicyRepository pricingRepo;
    private final QuoteSessionTotalsService quoteSessionTotalsService;
    private final QuoteSessionExpiryPolicy quoteSessionExpiryPolicy;
    private final EmailAuditService emailAuditService;
    private final CustomQuoteRequestNotificationService contactRequestNotificationService;

    public AdminOperationsControllerService(FilamentVariantStockKgRepository filamentStockRepo,
                                            FilamentVariantRepository filamentVariantRepo,
                                            CustomQuoteRequestRepository customQuoteRequestRepo,
                                            CustomQuoteRequestAttachmentRepository customQuoteRequestAttachmentRepo,
                                            EmailLogRepository emailLogRepo,
                                            QuoteSessionRepository quoteSessionRepo,
                                            QuoteLineItemRepository quoteLineItemRepo,
                                            OrderRepository orderRepo,
                                            PricingPolicyRepository pricingRepo,
                                            QuoteSessionTotalsService quoteSessionTotalsService,
                                            QuoteSessionExpiryPolicy quoteSessionExpiryPolicy,
                                            EmailAuditService emailAuditService,
                                            CustomQuoteRequestNotificationService contactRequestNotificationService) {
        this.filamentStockRepo = filamentStockRepo;
        this.filamentVariantRepo = filamentVariantRepo;
        this.customQuoteRequestRepo = customQuoteRequestRepo;
        this.customQuoteRequestAttachmentRepo = customQuoteRequestAttachmentRepo;
        this.emailLogRepo = emailLogRepo;
        this.quoteSessionRepo = quoteSessionRepo;
        this.quoteLineItemRepo = quoteLineItemRepo;
        this.orderRepo = orderRepo;
        this.pricingRepo = pricingRepo;
        this.quoteSessionTotalsService = quoteSessionTotalsService;
        this.quoteSessionExpiryPolicy = quoteSessionExpiryPolicy;
        this.emailAuditService = emailAuditService;
        this.contactRequestNotificationService = contactRequestNotificationService;
    }

    public List<AdminFilamentStockDto> getFilamentStock() {
        List<FilamentVariantStockKg> stocks = filamentStockRepo.findAll(Sort.by(Sort.Direction.ASC, "stockKg"));
        Set<Long> variantIds = stocks.stream()
                .map(FilamentVariantStockKg::getFilamentVariantId)
                .collect(Collectors.toSet());

        Map<Long, FilamentVariant> variantsById;
        if (variantIds.isEmpty()) {
            variantsById = Collections.emptyMap();
        } else {
            variantsById = filamentVariantRepo.findAllById(variantIds).stream()
                    .collect(Collectors.toMap(FilamentVariant::getId, variant -> variant));
        }

        return stocks.stream().map(stock -> {
            FilamentVariant variant = variantsById.get(stock.getFilamentVariantId());
            AdminFilamentStockDto dto = new AdminFilamentStockDto();
            dto.setFilamentVariantId(stock.getFilamentVariantId());
            dto.setStockSpools(stock.getStockSpools());
            dto.setSpoolNetKg(stock.getSpoolNetKg());
            dto.setStockKg(stock.getStockKg());
            BigDecimal grams = stock.getStockKg() != null
                    ? stock.getStockKg().multiply(BigDecimal.valueOf(1000))
                    : BigDecimal.ZERO;
            dto.setStockFilamentGrams(grams);

            if (variant != null) {
                dto.setMaterialCode(
                        variant.getFilamentMaterialType() != null
                                ? variant.getFilamentMaterialType().getMaterialCode()
                                : "UNKNOWN"
                );
                dto.setVariantDisplayName(variant.getVariantDisplayName());
                dto.setColorName(variant.getColorName());
                dto.setActive(variant.getIsActive());
            } else {
                dto.setMaterialCode("UNKNOWN");
                dto.setVariantDisplayName("Variant " + stock.getFilamentVariantId());
                dto.setColorName("-");
                dto.setActive(false);
            }

            return dto;
        }).toList();
    }

    public List<AdminContactRequestDto> getContactRequests() {
        return customQuoteRequestRepo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toContactRequestDto)
                .toList();
    }

    public AdminContactRequestDetailDto getContactRequestDetail(UUID requestId) {
        CustomQuoteRequest request = customQuoteRequestRepo.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Contact request not found"));

        List<AdminContactRequestAttachmentDto> attachments = customQuoteRequestAttachmentRepo
                .findByRequest_IdOrderByCreatedAtAsc(requestId)
                .stream()
                .map(this::toContactRequestAttachmentDto)
                .toList();

        return toContactRequestDetailDto(request, attachments);
    }

    @Transactional
    public AdminContactRequestDetailDto updateContactRequestStatus(UUID requestId,
                                                                   AdminUpdateContactRequestStatusRequest payload) {
        CustomQuoteRequest request = customQuoteRequestRepo.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Contact request not found"));

        String requestedStatus = payload != null && payload.getStatus() != null
                ? payload.getStatus().trim().toUpperCase(Locale.ROOT)
                : "";

        if (!CONTACT_REQUEST_ALLOWED_STATUSES.contains(requestedStatus)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Invalid status. Allowed: " + String.join(", ", CONTACT_REQUEST_ALLOWED_STATUSES)
            );
        }

        request.setStatus(requestedStatus);
        request.setUpdatedAt(OffsetDateTime.now());
        CustomQuoteRequest saved = customQuoteRequestRepo.save(request);

        List<AdminContactRequestAttachmentDto> attachments = customQuoteRequestAttachmentRepo
                .findByRequest_IdOrderByCreatedAtAsc(requestId)
                .stream()
                .map(this::toContactRequestAttachmentDto)
                .toList();

        return toContactRequestDetailDto(saved, attachments);
    }

    @Transactional
    public AdminContactRequestDetailDto resendContactRequestEmail(UUID requestId, UUID emailLogId) {
        CustomQuoteRequest request = customQuoteRequestRepo.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Contact request not found"));
        EmailLog emailLog = emailLogRepo.findById(emailLogId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Email log not found"));
        if (emailLog.getContactRequest() == null || !emailLog.getContactRequest().getId().equals(requestId)) {
            throw new ResponseStatusException(NOT_FOUND, "Email log not found for contact request");
        }

        int attachmentsCount = customQuoteRequestAttachmentRepo.findByRequest_IdOrderByCreatedAtAsc(requestId).size();
        contactRequestNotificationService.resendNotification(request, attachmentsCount, emailLog);
        return getContactRequestDetail(requestId);
    }

    public ResponseEntity<Resource> downloadContactRequestAttachment(UUID requestId, UUID attachmentId) {
        CustomQuoteRequestAttachment attachment = customQuoteRequestAttachmentRepo.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Attachment not found"));

        if (!attachment.getRequest().getId().equals(requestId)) {
            throw new ResponseStatusException(NOT_FOUND, "Attachment not found for request");
        }

        String relativePath = attachment.getStoredRelativePath();
        if (relativePath == null || relativePath.isBlank() || "PENDING".equals(relativePath)) {
            throw new ResponseStatusException(NOT_FOUND, "Attachment file not available");
        }

        String expectedPrefix = "quote-requests/" + requestId + "/attachments/" + attachmentId + "/";
        if (!relativePath.startsWith(expectedPrefix)) {
            throw new ResponseStatusException(NOT_FOUND, "Attachment file not available");
        }

        Path filePath = CONTACT_ATTACHMENTS_ROOT.resolve(relativePath).normalize();
        if (!filePath.startsWith(CONTACT_ATTACHMENTS_ROOT)) {
            throw new ResponseStatusException(NOT_FOUND, "Attachment file not available");
        }

        if (!Files.exists(filePath)) {
            throw new ResponseStatusException(NOT_FOUND, "Attachment file not available");
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(NOT_FOUND, "Attachment file not available");
            }

            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            String mimeType = attachment.getMimeType();
            if (mimeType != null && !mimeType.isBlank()) {
                try {
                    mediaType = MediaType.parseMediaType(mimeType);
                } catch (Exception ignored) {
                    mediaType = MediaType.APPLICATION_OCTET_STREAM;
                }
            }

            String filename = attachment.getOriginalFilename();
            if (filename == null || filename.isBlank()) {
                filename = attachment.getStoredFilename() != null && !attachment.getStoredFilename().isBlank()
                        ? attachment.getStoredFilename()
                        : "attachment-" + attachmentId;
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(filename, StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .body(resource);
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(NOT_FOUND, "Attachment file not available");
        }
    }

    public List<AdminQuoteSessionDto> getQuoteSessions() {
        return quoteSessionRepo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toQuoteSessionDto)
                .toList();
    }

    public AdminSessionStatisticsDto getSessionStatistics() {
        long totalSessions = quoteSessionRepo.countAllForStatistics();
        long sessionsWithItems = quoteSessionRepo.countWithItemsForStatistics();
        long totalLineItems = quoteSessionRepo.countLineItemsForStatistics();
        long paidConvertedSessions = quoteSessionRepo.countPaidConvertedForStatistics();

        AdminSessionStatisticsDto dto = new AdminSessionStatisticsDto();
        dto.setTotalSessionCount(totalSessions);
        dto.setSessionsWithItemsCount(sessionsWithItems);
        dto.setEmptySessionCount(Math.max(0, totalSessions - sessionsWithItems));
        dto.setConvertedSessionCount(quoteSessionRepo.countConvertedForStatistics());
        dto.setPaidConvertedSessionCount(paidConvertedSessions);
        dto.setModifiedSessionCount(quoteSessionRepo.countModifiedForStatistics());
        dto.setExpiredAbandonedSessionCount(
                quoteSessionRepo.countExpiredWithoutPaidConversionForStatistics(OffsetDateTime.now())
        );
        dto.setAverageItemsPerActiveSession(divide(totalLineItems, sessionsWithItems));
        dto.setPaidConversionRatePercent(divideAsPercent(paidConvertedSessions, sessionsWithItems));
        return dto;
    }

    public List<AdminCadInvoiceDto> getCadInvoices() {
        return quoteSessionRepo.findByStatusInOrderByCreatedAtDesc(List.of("CAD_ACTIVE", "CONVERTED"))
                .stream()
                .filter(this::isCadSessionRecord)
                .map(this::toCadInvoiceDto)
                .toList();
    }

    @Transactional
    public AdminCadInvoiceDto createOrUpdateCadInvoice(AdminCadInvoiceCreateRequest payload) {
        if (payload == null || payload.getCadHours() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "cadHours is required");
        }

        BigDecimal cadHours = payload.getCadHours().setScale(2, RoundingMode.HALF_UP);
        if (cadHours.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "cadHours must be > 0");
        }

        BigDecimal cadRate = payload.getCadHourlyRateChf();
        if (cadRate == null || cadRate.compareTo(BigDecimal.ZERO) <= 0) {
            var policy = pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc();
            cadRate = policy != null && policy.getCadCostChfPerHour() != null
                    ? policy.getCadCostChfPerHour()
                    : BigDecimal.ZERO;
        }
        cadRate = cadRate.setScale(2, RoundingMode.HALF_UP);

        QuoteSession session;
        if (payload.getSessionId() != null) {
            session = quoteSessionRepo.findById(payload.getSessionId())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Session not found"));
        } else {
            session = new QuoteSession();
            session.setStatus("CAD_ACTIVE");
            session.setSessionType("PRINT_QUOTE");
            session.setPricingVersion("v1");
            session.setMaterialCode("PLA");
            session.setNozzleDiameterMm(BigDecimal.valueOf(0.4));
            session.setLayerHeightMm(BigDecimal.valueOf(0.2));
            session.setInfillPattern("grid");
            session.setInfillPercent(20);
            session.setSupportsEnabled(false);
            session.setSetupCostChf(BigDecimal.ZERO);
            session.setCreatedAt(OffsetDateTime.now());
            session.setExpiresAt(quoteSessionExpiryPolicy.newExpiry());
        }

        if ("CONVERTED".equals(session.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Session already converted to order");
        }

        if (payload.getSourceRequestId() != null) {
            if (!customQuoteRequestRepo.existsById(payload.getSourceRequestId())) {
                throw new ResponseStatusException(NOT_FOUND, "Source request not found");
            }
            session.setSourceRequestId(payload.getSourceRequestId());
        } else {
            session.setSourceRequestId(null);
        }

        session.setStatus("CAD_ACTIVE");
        session.setCadHours(cadHours);
        session.setCadHourlyRateChf(cadRate);
        if (payload.getNotes() != null) {
            String trimmedNotes = payload.getNotes().trim();
            session.setNotes(trimmedNotes.isEmpty() ? null : trimmedNotes);
        }

        QuoteSession saved = quoteSessionRepo.save(session);
        return toCadInvoiceDto(saved);
    }

    @Transactional
    public void deleteQuoteSession(UUID sessionId) {
        QuoteSession session = quoteSessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Session not found"));

        if (orderRepo.existsBySourceQuoteSession_Id(sessionId)) {
            throw new ResponseStatusException(CONFLICT, "Cannot delete session already linked to an order");
        }

        deleteSessionFiles(sessionId);
        quoteSessionRepo.delete(session);
    }

    private AdminContactRequestDto toContactRequestDto(CustomQuoteRequest request) {
        AdminContactRequestDto dto = new AdminContactRequestDto();
        dto.setId(request.getId());
        dto.setRequestType(request.getRequestType());
        dto.setCustomerType(request.getCustomerType());
        dto.setEmail(request.getEmail());
        dto.setPhone(request.getPhone());
        dto.setName(request.getName());
        dto.setCompanyName(request.getCompanyName());
        dto.setStatus(request.getStatus());
        dto.setCreatedAt(request.getCreatedAt());
        return dto;
    }

    private BigDecimal divideAsPercent(long dividend, long divisor) {
        if (divisor == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(dividend)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal divide(long dividend, long divisor) {
        if (divisor == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(dividend)
                .divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
    }

    private AdminContactRequestAttachmentDto toContactRequestAttachmentDto(CustomQuoteRequestAttachment attachment) {
        AdminContactRequestAttachmentDto dto = new AdminContactRequestAttachmentDto();
        dto.setId(attachment.getId());
        dto.setOriginalFilename(attachment.getOriginalFilename());
        dto.setMimeType(attachment.getMimeType());
        dto.setFileSizeBytes(attachment.getFileSizeBytes());
        dto.setCreatedAt(attachment.getCreatedAt());
        return dto;
    }

    private AdminContactRequestDetailDto toContactRequestDetailDto(CustomQuoteRequest request,
                                                                    List<AdminContactRequestAttachmentDto> attachments) {
        AdminContactRequestDetailDto dto = new AdminContactRequestDetailDto();
        dto.setId(request.getId());
        dto.setRequestType(request.getRequestType());
        dto.setCustomerType(request.getCustomerType());
        dto.setEmail(request.getEmail());
        dto.setPhone(request.getPhone());
        dto.setName(request.getName());
        dto.setCompanyName(request.getCompanyName());
        dto.setContactPerson(request.getContactPerson());
        dto.setMessage(request.getMessage());
        dto.setStatus(request.getStatus());
        dto.setCreatedAt(request.getCreatedAt());
        dto.setUpdatedAt(request.getUpdatedAt());
        dto.setAttachments(attachments);
        dto.setEmailLogs(emailAuditService.getContactRequestEmailLogDtos(request.getId()));
        return dto;
    }

    private AdminQuoteSessionDto toQuoteSessionDto(QuoteSession session) {
        AdminQuoteSessionDto dto = new AdminQuoteSessionDto();
        dto.setId(session.getId());
        dto.setStatus(session.getStatus());
        dto.setSessionType(session.getSessionType() != null ? session.getSessionType() : "PRINT_QUOTE");
        dto.setMaterialCode(session.getMaterialCode());
        dto.setCreatedAt(session.getCreatedAt());
        dto.setExpiresAt(session.getExpiresAt());
        dto.setConvertedOrderId(session.getConvertedOrderId());
        dto.setSourceRequestId(session.getSourceRequestId());
        dto.setCadHours(session.getCadHours());
        dto.setCadHourlyRateChf(session.getCadHourlyRateChf());
        dto.setCadTotalChf(quoteSessionTotalsService.calculateCadTotal(session));
        return dto;
    }

    private boolean isCadSessionRecord(QuoteSession session) {
        if ("CAD_ACTIVE".equals(session.getStatus())) {
            return true;
        }
        if (!"CONVERTED".equals(session.getStatus())) {
            return false;
        }
        BigDecimal cadHours = session.getCadHours() != null ? session.getCadHours() : BigDecimal.ZERO;
        return cadHours.compareTo(BigDecimal.ZERO) > 0 || session.getSourceRequestId() != null;
    }

    private AdminCadInvoiceDto toCadInvoiceDto(QuoteSession session) {
        List<QuoteLineItem> items = quoteLineItemRepo.findByQuoteSessionId(session.getId());
        QuoteSessionTotalsService.QuoteSessionTotals totals = quoteSessionTotalsService.compute(session, items);

        AdminCadInvoiceDto dto = new AdminCadInvoiceDto();
        dto.setSessionId(session.getId());
        dto.setSessionStatus(session.getStatus());
        dto.setSourceRequestId(session.getSourceRequestId());
        dto.setCadHours(session.getCadHours() != null ? session.getCadHours() : BigDecimal.ZERO);
        dto.setCadHourlyRateChf(session.getCadHourlyRateChf() != null ? session.getCadHourlyRateChf() : BigDecimal.ZERO);
        dto.setCadTotalChf(totals.cadTotalChf());
        dto.setPrintItemsTotalChf(totals.printItemsTotalChf());
        dto.setSetupCostChf(totals.setupCostChf());
        dto.setShippingCostChf(totals.shippingCostChf());
        dto.setGrandTotalChf(totals.grandTotalChf());
        dto.setConvertedOrderId(session.getConvertedOrderId());
        dto.setCheckoutPath("/checkout/cad?session=" + session.getId());
        dto.setNotes(session.getNotes());
        dto.setCreatedAt(session.getCreatedAt());

        if (session.getConvertedOrderId() != null) {
            Order order = orderRepo.findById(session.getConvertedOrderId()).orElse(null);
            dto.setConvertedOrderStatus(order != null ? order.getStatus() : null);
        }
        return dto;
    }

    private void deleteSessionFiles(UUID sessionId) {
        Path sessionDir = Paths.get("storage_quotes", sessionId.toString());
        if (!Files.exists(sessionDir)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(sessionDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            logger.error("Failed to delete files for session {}", sessionId, e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Unable to delete session files");
        }
    }
}

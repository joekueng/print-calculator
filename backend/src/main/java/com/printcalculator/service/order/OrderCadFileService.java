package com.printcalculator.service.order;

import com.printcalculator.dto.OrderDeliverableFileDto;
import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderDeliverableFile;
import com.printcalculator.entity.OrderItem;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.OrderDeliverableFileRepository;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.PaymentRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.service.storage.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE;

@Service
@Transactional(readOnly = true)
public class OrderCadFileService {
    private static final Path QUOTE_STORAGE_ROOT = Paths.get("storage_quotes").toAbsolutePath().normalize();
    private static final String SHOP_LINE_ITEM_TYPE = "SHOP_PRODUCT";
    private static final Pattern SAFE_EXTENSION_PATTERN = Pattern.compile("^[a-z0-9]{1,10}$");

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final OrderDeliverableFileRepository deliverableFileRepo;
    private final PaymentRepository paymentRepo;
    private final QuoteLineItemRepository quoteLineItemRepo;
    private final StorageService storageService;

    @Value("${app.cad.upload.max-file-size-bytes:104857600}")
    private long maxCadUploadSizeBytes;

    @Value("${app.cad.upload.max-files-per-request:20}")
    private int maxCadFilesPerRequest;

    public OrderCadFileService(OrderRepository orderRepo,
                               OrderItemRepository orderItemRepo,
                               OrderDeliverableFileRepository deliverableFileRepo,
                               PaymentRepository paymentRepo,
                               QuoteLineItemRepository quoteLineItemRepo,
                               StorageService storageService) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.deliverableFileRepo = deliverableFileRepo;
        this.paymentRepo = paymentRepo;
        this.quoteLineItemRepo = quoteLineItemRepo;
        this.storageService = storageService;
    }

    public CadFileSummary summarize(Order order) {
        if (order == null || !Boolean.TRUE.equals(order.getIsCadOrder()) || order.getId() == null) {
            return new CadFileSummary(0, false);
        }

        int fileCount = countBaseCadFiles(order) + (int) deliverableFileRepo.countByOrder_Id(order.getId());
        return new CadFileSummary(fileCount, fileCount > 0 && isPaymentConfirmed(order));
    }

    public List<OrderDeliverableFileDto> listDeliverableDtos(UUID orderId) {
        return deliverableFileRepo.findByOrder_IdOrderByCreatedAtAsc(orderId).stream()
                .map(this::toDto)
                .toList();
    }

    public ResponseEntity<?> downloadCustomerCadFiles(UUID orderId) {
        Order order = getOrderOrThrow(orderId);
        if (!Boolean.TRUE.equals(order.getIsCadOrder())) {
            throw new ResponseStatusException(NOT_FOUND, "CAD files not available");
        }
        if (!isPaymentConfirmed(order)) {
            throw new ResponseStatusException(FORBIDDEN, "CAD files are available after payment confirmation");
        }

        List<DownloadableCadFile> files = collectDownloadableCadFiles(order);
        if (files.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "CAD files not available");
        }

        if (files.size() == 1) {
            DownloadableCadFile file = files.getFirst();
            return ResponseEntity.ok()
                    .contentType(file.contentType())
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(file.filename(), StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .body(file.resource());
        }

        String orderNumber = getDisplayOrderNumber(order);
        StreamingResponseBody stream = outputStream -> {
            try (ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
                Map<String, Integer> usedNames = new HashMap<>();
                for (DownloadableCadFile file : files) {
                    String entryName = uniqueZipEntryName(safeZipEntryName(file.filename()), usedNames);
                    zip.putNextEntry(new ZipEntry(entryName));
                    try (InputStream input = file.resource().getInputStream()) {
                        input.transferTo(zip);
                    }
                    zip.closeEntry();
                }
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("cad-files-" + orderNumber + ".zip", StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(stream);
    }

    @Transactional
    public List<OrderDeliverableFileDto> uploadAdminCadFiles(UUID orderId, List<MultipartFile> files) {
        Order order = getOrderOrThrow(orderId);
        if (!Boolean.TRUE.equals(order.getIsCadOrder())) {
            throw new ResponseStatusException(BAD_REQUEST, "CAD files can only be added to CAD orders");
        }
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one file is required");
        }
        if (files.size() > maxCadFilesPerRequest) {
            throw new ResponseStatusException(
                    PAYLOAD_TOO_LARGE,
                    "Too many files. Maximum " + maxCadFilesPerRequest + " files per request"
            );
        }

        List<OrderDeliverableFileDto> savedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (maxCadUploadSizeBytes > 0 && file.getSize() > maxCadUploadSizeBytes) {
                throw new ResponseStatusException(
                        PAYLOAD_TOO_LARGE,
                        "File \"" + safeOriginalFilename(file.getOriginalFilename())
                                + "\" exceeds the maximum allowed size of " + maxCadUploadSizeBytes + " bytes"
                );
            }
            savedFiles.add(toDto(storeDeliverableFile(order, file)));
        }

        if (savedFiles.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one non-empty file is required");
        }

        return savedFiles;
    }

    @Transactional
    public void deleteAdminCadFile(UUID orderId, UUID fileId) {
        OrderDeliverableFile deliverable = deliverableFileRepo.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "CAD file not found"));
        if (!deliverable.getOrder().getId().equals(orderId)) {
            throw new ResponseStatusException(NOT_FOUND, "CAD file not found for order");
        }

        String storedRelativePath = deliverable.getStoredRelativePath();
        if (storedRelativePath != null && !storedRelativePath.isBlank() && !"PENDING".equals(storedRelativePath)) {
            Path safePath = resolveDeliverableRelativePath(storedRelativePath, orderId, fileId);
            if (safePath != null) {
                try {
                    storageService.delete(safePath);
                } catch (IOException ignored) {
                    // The metadata should still be removable when a file is already gone.
                }
            }
        }

        deliverableFileRepo.delete(deliverable);
    }

    private OrderDeliverableFile storeDeliverableFile(Order order, MultipartFile file) {
        String originalFilename = safeOriginalFilename(file.getOriginalFilename());
        String ext = getSafeExtension(originalFilename);
        String storedFilename = UUID.randomUUID() + "." + ext;

        OrderDeliverableFile deliverable = new OrderDeliverableFile();
        deliverable.setOrder(order);
        deliverable.setOriginalFilename(originalFilename);
        deliverable.setStoredFilename(storedFilename);
        deliverable.setStoredRelativePath("PENDING");
        deliverable.setMimeType(file.getContentType());
        deliverable.setFileSizeBytes(file.getSize());
        deliverable.setCreatedAt(OffsetDateTime.now());
        deliverable = deliverableFileRepo.save(deliverable);

        Path relativePath = Path.of(
                "orders",
                order.getId().toString(),
                "cad-deliverables",
                deliverable.getId().toString(),
                storedFilename
        );

        try {
            storageService.store(file, relativePath);
        } catch (IOException e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to store CAD file", e);
        }

        deliverable.setStoredRelativePath(relativePath.toString());
        return deliverableFileRepo.save(deliverable);
    }

    private List<DownloadableCadFile> collectDownloadableCadFiles(Order order) {
        List<DownloadableCadFile> files = new ArrayList<>();
        for (OrderItem item : orderItemRepo.findByOrder_Id(order.getId())) {
            if (!isBaseCadFile(item)) {
                continue;
            }
            Path safePath = resolveOrderItemRelativePath(item.getStoredRelativePath(), order.getId(), item.getId());
            if (safePath == null) {
                continue;
            }
            Resource resource = loadOrderItemResourceWithRecovery(item, safePath);
            if (resource == null) {
                continue;
            }
            files.add(new DownloadableCadFile(
                    resource,
                    safeDownloadFilename(item.getOriginalFilename(), "cad-file-" + item.getId()),
                    mediaTypeOrOctetStream(item.getMimeType())
            ));
        }

        for (OrderDeliverableFile deliverable : deliverableFileRepo.findByOrder_IdOrderByCreatedAtAsc(order.getId())) {
            Path safePath = resolveDeliverableRelativePath(
                    deliverable.getStoredRelativePath(),
                    order.getId(),
                    deliverable.getId()
            );
            if (safePath == null) {
                continue;
            }
            try {
                files.add(new DownloadableCadFile(
                        storageService.loadAsResource(safePath),
                        safeDownloadFilename(deliverable.getOriginalFilename(), "cad-file-" + deliverable.getId()),
                        mediaTypeOrOctetStream(deliverable.getMimeType())
                ));
            } catch (Exception ignored) {
                // Skip stale deliverable metadata; if all files are stale the caller returns 404.
            }
        }
        return files;
    }

    private int countBaseCadFiles(Order order) {
        int count = 0;
        for (OrderItem item : orderItemRepo.findByOrder_Id(order.getId())) {
            if (isBaseCadFile(item)
                    && resolveOrderItemRelativePath(item.getStoredRelativePath(), order.getId(), item.getId()) != null) {
                count += 1;
            }
        }
        return count;
    }

    private boolean isBaseCadFile(OrderItem item) {
        if (item == null || item.getId() == null) {
            return false;
        }
        String type = item.getItemType() != null ? item.getItemType() : "";
        if (SHOP_LINE_ITEM_TYPE.equalsIgnoreCase(type)) {
            return false;
        }
        String relativePath = item.getStoredRelativePath();
        return relativePath != null && !relativePath.isBlank() && !"PENDING".equals(relativePath);
    }

    private boolean isPaymentConfirmed(Order order) {
        if (order.getPaidAt() != null || "PAID".equalsIgnoreCase(order.getStatus())) {
            return true;
        }
        return paymentRepo.findByOrder_Id(order.getId())
                .map(payment -> "RECEIVED".equalsIgnoreCase(payment.getStatus())
                        || "COMPLETED".equalsIgnoreCase(payment.getStatus()))
                .orElse(false);
    }

    private Resource loadOrderItemResourceWithRecovery(OrderItem item, Path safeRelativePath) {
        try {
            return storageService.loadAsResource(safeRelativePath);
        } catch (Exception primaryFailure) {
            Path sourceQuotePath = resolveFallbackQuoteItemPath(item);
            if (sourceQuotePath == null) {
                return null;
            }
            try {
                storageService.store(sourceQuotePath, safeRelativePath);
                return storageService.loadAsResource(safeRelativePath);
            } catch (Exception copyFailure) {
                try {
                    Resource quoteResource = new UrlResource(sourceQuotePath.toUri());
                    if (quoteResource.exists() || quoteResource.isReadable()) {
                        return quoteResource;
                    }
                } catch (Exception ignored) {
                    // fall through to null
                }
                return null;
            }
        }
    }

    private Path resolveFallbackQuoteItemPath(OrderItem orderItem) {
        Order order = orderItem.getOrder();
        QuoteSession sourceSession = order != null ? order.getSourceQuoteSession() : null;
        UUID sourceSessionId = sourceSession != null ? sourceSession.getId() : null;
        if (sourceSessionId == null) {
            return null;
        }

        String targetFilename = normalizeFilename(orderItem.getOriginalFilename());
        if (targetFilename == null) {
            return null;
        }

        return quoteLineItemRepo.findByQuoteSessionId(sourceSessionId).stream()
                .filter(quoteItem -> targetFilename.equals(normalizeFilename(quoteItem.getOriginalFilename())))
                .sorted(Comparator.comparingInt((QuoteLineItem quoteItem) -> scoreQuoteMatch(orderItem, quoteItem)).reversed())
                .map(quoteItem -> resolveStoredQuotePath(quoteItem.getStoredPath(), sourceSessionId))
                .filter(path -> path != null && Files.exists(path))
                .findFirst()
                .orElse(null);
    }

    private int scoreQuoteMatch(OrderItem orderItem, QuoteLineItem quoteItem) {
        int score = 0;
        if (orderItem.getQuantity() != null && orderItem.getQuantity().equals(quoteItem.getQuantity())) {
            score += 4;
        }
        if (orderItem.getPrintTimeSeconds() != null && orderItem.getPrintTimeSeconds().equals(quoteItem.getPrintTimeSeconds())) {
            score += 3;
        }
        if (orderItem.getMaterialCode() != null
                && quoteItem.getMaterialCode() != null
                && orderItem.getMaterialCode().equalsIgnoreCase(quoteItem.getMaterialCode())) {
            score += 3;
        }
        if (orderItem.getMaterialGrams() != null
                && quoteItem.getMaterialGrams() != null
                && orderItem.getMaterialGrams().compareTo(quoteItem.getMaterialGrams()) == 0) {
            score += 2;
        }
        return score;
    }

    private Path resolveStoredQuotePath(String storedPath, UUID expectedSessionId) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }
        try {
            Path raw = Path.of(storedPath).normalize();
            Path resolved = raw.isAbsolute() ? raw : QUOTE_STORAGE_ROOT.resolve(raw).normalize();
            Path expectedSessionRoot = QUOTE_STORAGE_ROOT.resolve(expectedSessionId.toString()).normalize();
            if (!resolved.startsWith(expectedSessionRoot)) {
                return null;
            }
            return resolved;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private Path resolveOrderItemRelativePath(String storedRelativePath, UUID orderId, UUID orderItemId) {
        try {
            Path candidate = Path.of(storedRelativePath).normalize();
            if (candidate.isAbsolute()) {
                return null;
            }
            Path expectedPrefix = Path.of("orders", orderId.toString(), "3d-files", orderItemId.toString());
            if (!candidate.startsWith(expectedPrefix)) {
                return null;
            }
            return candidate;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private Path resolveDeliverableRelativePath(String storedRelativePath, UUID orderId, UUID deliverableId) {
        try {
            Path candidate = Path.of(storedRelativePath).normalize();
            if (candidate.isAbsolute()) {
                return null;
            }
            Path expectedPrefix = Path.of("orders", orderId.toString(), "cad-deliverables", deliverableId.toString());
            if (!candidate.startsWith(expectedPrefix)) {
                return null;
            }
            return candidate;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private String uniqueZipEntryName(String filename, Map<String, Integer> usedNames) {
        String candidate = filename;
        int count = 1;
        int dot = filename.lastIndexOf('.');
        while (usedNames.containsKey(candidate)) {
            count++;
            candidate = dot > 0 ? filename.substring(0, dot) + "-" + count + filename.substring(dot)
                    : filename + "-" + count;
        }
        usedNames.put(candidate, 1);
        return candidate;
    }

    private String safeZipEntryName(String filename) {
        String safe = safeDownloadFilename(filename, "cad-file")
                .replace('\\', '_')
                .replace('/', '_')
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        return safe.isBlank() ? "cad-file" : safe;
    }

    private MediaType mediaTypeOrOctetStream(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String safeOriginalFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "cad-file";
        }
        String cleaned = StringUtils.cleanPath(filename);
        if (cleaned.contains("..")) {
            return "cad-file";
        }
        return cleaned;
    }

    private String safeDownloadFilename(String filename, String fallback) {
        String cleaned = safeOriginalFilename(filename);
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private String getSafeExtension(String filename) {
        String cleaned = safeOriginalFilename(filename);
        int i = cleaned.lastIndexOf('.');
        if (i > 0 && i < cleaned.length() - 1) {
            String ext = cleaned.substring(i + 1).toLowerCase(Locale.ROOT);
            if (SAFE_EXTENSION_PATTERN.matcher(ext).matches()) {
                return ext;
            }
        }
        return "bin";
    }

    private String normalizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        return filename.trim();
    }

    private String getDisplayOrderNumber(Order order) {
        String orderNumber = order.getOrderNumber();
        if (orderNumber != null && !orderNumber.isBlank()) {
            return orderNumber;
        }
        return order.getId() != null ? order.getId().toString() : "unknown";
    }

    private Order getOrderOrThrow(UUID orderId) {
        return orderRepo.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Order not found"));
    }

    private OrderDeliverableFileDto toDto(OrderDeliverableFile file) {
        OrderDeliverableFileDto dto = new OrderDeliverableFileDto();
        dto.setId(file.getId());
        dto.setOriginalFilename(file.getOriginalFilename());
        dto.setFileSizeBytes(file.getFileSizeBytes());
        dto.setMimeType(file.getMimeType());
        dto.setCreatedAt(file.getCreatedAt());
        return dto;
    }

    public record CadFileSummary(int fileCount, boolean downloadAvailable) {
    }

    private record DownloadableCadFile(Resource resource, String filename, MediaType contentType) {
    }
}

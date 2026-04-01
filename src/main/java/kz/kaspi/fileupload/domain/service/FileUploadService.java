package kz.kaspi.fileupload.domain.service;

import kz.kaspi.fileupload.api.dto.FileStatusResponse;
import kz.kaspi.fileupload.api.dto.UploadResponse;
import kz.kaspi.fileupload.domain.model.FileUpload;
import kz.kaspi.fileupload.domain.model.UploadStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final IdempotencyService idempotencyService;
    private final MetadataService metadataService;
    private final StorageService storageService;

    @Value("${app.upload.max-file-size}")
    private long maxFileSize;

    @Value("${app.upload.allowed-extensions}")
    private List<String> allowedExtensions;

    public Mono<UploadResponse> initiateUpload(FilePart filePart, String userId) {
        log.info("Initiating upload for file: {}, user: {}", filePart.filename(), userId);

        String filename = filePart.filename();
        String contentType = getContentType(filePart);

        return validateFileExtension(filename)
                .then(readFilePart(filePart))
                .flatMap(fileBytes -> {
                    log.debug("File read into memory: {} bytes", fileBytes.length);

                    if (fileBytes.length > maxFileSize) {
                        return Mono.error(new IllegalArgumentException(
                                String.format("File size exceeds limit: %d bytes (max: %d)",
                                        fileBytes.length, maxFileSize)
                        ));
                    }

                    String fileHash = calculateHash(fileBytes);
                    log.debug("File hash: {}", fileHash);

                    return processUpload(filename, contentType, fileBytes, fileHash, userId);
                })
                .doOnError(error -> log.error("Upload initiation failed: {}", error.getMessage()));
    }

    private Mono<byte[]> readFilePart(FilePart filePart) {
        return DataBufferUtils.join(filePart.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String calculateHash(byte[] bytes) {
        return DigestUtils.sha256Hex(bytes);
    }

    private Mono<UploadResponse> processUpload(
            String filename,
            String contentType,
            byte[] fileBytes,
            String fileHash,
            String userId
    ) {
        log.debug("Processing upload: filename={}, hash={}, size={}", filename, fileHash, fileBytes.length);

        return idempotencyService.checkDuplicateByHash(fileHash)
                .flatMap(existingUploadId -> {
                    log.info("Duplicate file detected: hash={}, uploadId={}", fileHash, existingUploadId);
                    return Mono.just(UploadResponse.builder()
                            .uploadId(existingUploadId)
                            .status("DUPLICATE")
                            .message("File already uploaded")
                            .build());
                })
                .switchIfEmpty(Mono.defer(() -> {
                    String uploadId = UUID.randomUUID().toString();
                    log.info("New file upload initiated: uploadId={}, filename={}, size={}",
                            uploadId, filename, fileBytes.length);

                    return initiateNewUpload(uploadId, filename, contentType, fileBytes, fileHash, userId);
                }))
                .doOnSuccess(response -> log.debug("processUpload completed: {}", response))
                .doOnError(error -> log.error("processUpload failed: {}", error.getMessage(), error));
    }

    private Mono<UploadResponse> initiateNewUpload(
            String uploadId,
            String filename,
            String contentType,
            byte[] fileBytes,
            String fileHash,
            String userId
    ) {
        log.debug("Initiating new upload: uploadId={}", uploadId);

        return idempotencyService.storeHashMapping(fileHash, uploadId)
                .doOnSuccess(result -> log.debug("Hash mapping stored: {} -> {}", fileHash, uploadId))
                .then(idempotencyService.storeUploadStatus(uploadId, "PROCESSING"))
                .doOnSuccess(result -> log.debug("Status stored: {} -> PROCESSING", uploadId))
                .then(createMetadata(uploadId, filename, contentType, fileHash, userId))
                .doOnSuccess(metadata -> log.debug("Metadata created: {}", metadata.getId()))
                .thenReturn(UploadResponse.builder()
                        .uploadId(uploadId)
                        .status("PROCESSING")
                        .message("Upload initiated successfully")
                        .build())
                .doOnSuccess(response -> {
                    log.info("Upload response ready: {}", response);
                    // Запускаем асинхронную обработку
                    log.info("Starting async upload processing for uploadId: {}", uploadId);
                    processUploadAsync(uploadId, filename, contentType, fileBytes, userId);
                })
                .doOnError(error -> log.error("initiateNewUpload failed: {}", error.getMessage(), error));
    }

    private Mono<FileUpload> createMetadata(
            String uploadId,
            String filename,
            String contentType,
            String fileHash,
            String userId
    ) {
        FileUpload metadata = FileUpload.builder()
                .id(uploadId)
                .originalFilename(filename)
                .contentType(contentType)
                .fileHash(fileHash)
                .uploadedBy(userId)
                .status(UploadStatus.PROCESSING)
                .build();

        return metadataService.saveMetadata(metadata);
    }

    private void processUploadAsync(
            String uploadId,
            String filename,
            String contentType,
            byte[] fileBytes,
            String userId
    ) {
        uploadToStorage(uploadId, filename, contentType, fileBytes)
                .flatMap(storageUrl ->
                        metadataService.updateMetadataOnSuccess(uploadId, storageUrl, (long) fileBytes.length)
                )
                .flatMap(metadata ->
                        idempotencyService.updateUploadStatus(uploadId, "COMPLETED")
                                .thenReturn(metadata)
                )
                .doOnSuccess(result ->
                        log.info("Upload completed successfully: uploadId={}", uploadId)
                )
                .onErrorResume(error -> {
                    log.error("Upload failed: uploadId={}, error={}", uploadId, error.getMessage(), error);

                    return metadataService.updateMetadataOnFailure(uploadId, error.getMessage())
                            .then(idempotencyService.updateUploadStatus(uploadId, "FAILED"))
                            .then(Mono.empty());
                })
                .subscribe();
    }

    private Mono<String> uploadToStorage(
            String uploadId,
            String filename,
            String contentType,
            byte[] fileBytes
    ) {
        return Mono.fromCallable(() -> {
                    log.debug("Uploading to storage: uploadId={}, size={} bytes", uploadId, fileBytes.length);
                    return storageService.uploadFileBytes(fileBytes, filename, uploadId, contentType).block();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<FileStatusResponse> getUploadStatus(String uploadId) {
        log.debug("Getting status for uploadId: {}", uploadId);

        return metadataService.getMetadata(uploadId)
                .map(this::mapToStatusResponse);
    }

    private Mono<Void> validateFileExtension(String filename) {
        String extension = getFileExtension(filename);

        if (extension.isEmpty()) {
            return Mono.error(new IllegalArgumentException("File has no extension"));
        }

        if (!allowedExtensions.contains(extension.toLowerCase())) {
            return Mono.error(new IllegalArgumentException(
                    String.format("File extension '%s' not allowed. Allowed: %s",
                            extension, allowedExtensions)
            ));
        }

        return Mono.empty();
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }

    private String getContentType(FilePart filePart) {
        return filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString()
                : "application/octet-stream";
    }

    private FileStatusResponse mapToStatusResponse(FileUpload fileUpload) {
        return FileStatusResponse.builder()
                .uploadId(fileUpload.getId())
                .filename(fileUpload.getOriginalFilename())
                .status(fileUpload.getStatus())
                .sizeBytes(fileUpload.getSizeBytes())
                .storageUrl(fileUpload.getStorageUrl())
                .uploadedAt(fileUpload.getCreatedAt())
                .errorMessage(fileUpload.getErrorMessage())
                .build();
    }
}
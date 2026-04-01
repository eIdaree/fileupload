package kz.kaspi.fileupload.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import kz.kaspi.fileupload.api.dto.FileStatusResponse;
import kz.kaspi.fileupload.api.dto.UploadResponse;
import kz.kaspi.fileupload.domain.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<UploadResponse>> uploadFile(
            @RequestPart("file") FilePart file,
            @RequestHeader("X-User-Id") String userId
    ) {
        log.info("Upload request received from user: {}, filename: {}", userId, file.filename());

        return fileUploadService.initiateUpload(file, userId)
                .map(response -> {

                    HttpStatus status = "DUPLICATE".equals(response.getStatus())
                            ? HttpStatus.CONFLICT
                            : HttpStatus.ACCEPTED;

                    return ResponseEntity.status(status).body(response);
                })
//                .timeout(Duration.ofSeconds(10))
                .doOnSuccess(responseEntity -> {
                    if (responseEntity != null && responseEntity.getBody() != null) {
                        log.info("Upload initiated successfully, uploadId: {}",
                                responseEntity.getBody().getUploadId());
                    }
                })
                .doOnError(error ->
                        log.error("Upload failed: {}", error.getMessage())
                );
    }

    @GetMapping("/{uploadId}/status")
    public Mono<ResponseEntity<FileStatusResponse>> getUploadStatus(
            @PathVariable String uploadId
    ) {
        log.debug("Status request for uploadId: {}", uploadId);

        return fileUploadService.getUploadStatus(uploadId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnSuccess(response -> {
                    if (response.getStatusCode() == HttpStatus.OK) {
                        log.debug("Status retrieved for uploadId: {}", uploadId);
                    } else {
                        log.warn("Upload not found: {}", uploadId);
                    }
                });
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<String>> health() {
        return Mono.just(ResponseEntity.ok("File upload service is running"));
    }
}

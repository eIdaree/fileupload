package kz.kaspi.fileupload.domain.service;

import kz.kaspi.fileupload.domain.model.FileUpload;
import kz.kaspi.fileupload.domain.model.UploadStatus;
import kz.kaspi.fileupload.domain.repository.FileUploadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataService {

    private final FileUploadRepository fileUploadRepository;


    public Mono<FileUpload> updateMetadataOnSuccess(
            String uploadId,
            String storageUrl,
            Long sizeBytes
    ) {
        return fileUploadRepository.findById(uploadId)
                .flatMap(fileUpload -> {
                    fileUpload.setStorageUrl(storageUrl);
                    fileUpload.setSizeBytes(sizeBytes);
                    fileUpload.setStatus(UploadStatus.COMPLETED);
                    fileUpload.setErrorMessage(null);

                    return fileUploadRepository.save(fileUpload);
                })
                .doOnSuccess(updated ->
                        log.info("Metadata updated on success for uploadId: {}", uploadId)
                );
    }

    public Mono<FileUpload> updateMetadataOnFailure(String uploadId, String errorMessage) {
        return fileUploadRepository.findById(uploadId)
                .flatMap(fileUpload -> {
                    fileUpload.setStatus(UploadStatus.FAILED);
                    fileUpload.setErrorMessage(errorMessage);

                    return fileUploadRepository.save(fileUpload);
                })
                .doOnSuccess(updated ->
                        log.error("Metadata updated on failure for uploadId: {}, error: {}", uploadId, errorMessage)
                );
    }

    public Mono<FileUpload> getMetadata(String uploadId) {
        return fileUploadRepository.findById(uploadId)
                .doOnSuccess(metadata -> {
                    if (metadata != null) {
                        log.debug("Metadata retrieved for uploadId: {}", uploadId);
                    } else {
                        log.debug("No metadata found for uploadId: {}", uploadId);
                    }
                });
    }

    private String getContentType(FilePart filePart) {
        return filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString()
                : "application/octet-stream";
    }

    public Mono<FileUpload> saveMetadata(FileUpload fileUpload) {
        return fileUploadRepository.save(fileUpload)
                .doOnSuccess(saved ->
                        log.info("Metadata saved: uploadId={}, filename={}",
                                saved.getId(), saved.getOriginalFilename())
                );
    }

}

package kz.kaspi.fileupload.domain.service;

import reactor.core.publisher.Mono;

public interface StorageService {
    Mono<String> uploadFileBytes(byte[] fileBytes, String filename, String uploadId, String contentType);
}

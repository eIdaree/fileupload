package kz.kaspi.fileupload.domain.repository;

import kz.kaspi.fileupload.domain.model.FileUpload;
import kz.kaspi.fileupload.domain.model.UploadStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface FileUploadRepository extends ReactiveMongoRepository<FileUpload, String> {

    Mono<FileUpload> findByFileHash(String fileHash);

    Flux<FileUpload> findByUploadedBy(String userId);

    Flux<FileUpload> findByStatus(UploadStatus status);

    Mono<Boolean> existsByFileHash(String fileHash);
}
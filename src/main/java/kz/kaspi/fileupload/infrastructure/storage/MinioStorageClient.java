package kz.kaspi.fileupload.infrastructure.storage;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import kz.kaspi.fileupload.domain.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageClient implements StorageService {

    private final MinioClient minioClient;

    @Value("${app.storage.minio.bucket-name}")
    private String bucketName;

    @Override
    public Mono<String> uploadFileBytes(byte[] fileBytes, String filename, String uploadId, String contentType) {
        log.debug("Uploading file bytes: uploadId={}, filename={}, size={} bytes",
                uploadId, filename, fileBytes.length);

        return Mono.fromCallable(() -> {
                    String objectName = generateObjectName(uploadId, filename);

                    minioClient.putObject(
                            PutObjectArgs.builder()
                                    .bucket(bucketName)
                                    .object(objectName)
                                    .stream(new ByteArrayInputStream(fileBytes), fileBytes.length, -1)
                                    .contentType(contentType)
                                    .build()
                    );

                    String url = String.format("minio://%s/%s", bucketName, objectName);
                    log.info("File uploaded to MinIO: {}", url);
                    return url;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String generateObjectName(String uploadId, String filename) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("%s/%s-%s", datePath, uploadId, filename);
    }

}
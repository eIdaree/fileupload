package kz.kaspi.fileupload.domain.service;

import kz.kaspi.fileupload.domain.model.FileUpload;
import kz.kaspi.fileupload.domain.repository.FileUploadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final FileUploadRepository fileUploadRepository;

    @Value("${app.idempotency.ttl-hours}")
    private long ttlHours;

    private static final String HASH_KEY_PREFIX = "upload:hash:";
    private static final String STATUS_KEY_PREFIX = "upload:status:";

    public Mono<String> calculateFileHash(FilePart filePart) {
        log.debug("Calculating hash for file: {}", filePart.filename());

        return DataBufferUtils.join(filePart.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    // Вычисляем SHA-256 хеш
                    String hash = DigestUtils.sha256Hex(bytes);
                    log.debug("File hash calculated: {}", hash);
                    return hash;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<String> checkDuplicateByHash(String fileHash) {
        String key = HASH_KEY_PREFIX + fileHash;

        return redisTemplate.opsForValue()
                .get(key)
                .doOnNext(uploadId ->
                        log.info("Duplicate file detected, hash: {}, uploadId: {}", fileHash, uploadId)
                )
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("No duplicate found for hash: {}", fileHash);
                    return Mono.empty();  // ✅ Важно: возвращаем пустой Mono, не null
                }));
    }

    public Mono<Boolean> storeHashMapping(String fileHash, String uploadId) {
        String key = HASH_KEY_PREFIX + fileHash;
        Duration ttl = Duration.ofHours(ttlHours);

        return redisTemplate.opsForValue()
                .set(key, uploadId, ttl)
                .doOnSuccess(result ->
                        log.debug("Hash mapping stored: {} -> {}, TTL: {} hours", fileHash, uploadId, ttlHours)
                );
    }

    public Mono<Boolean> storeUploadStatus(String uploadId, String status) {
        String key = STATUS_KEY_PREFIX + uploadId;
        Duration ttl = Duration.ofHours(ttlHours);

        return redisTemplate.opsForValue()
                .set(key, status, ttl)
                .doOnSuccess(result ->
                        log.debug("Upload status stored: {} -> {}", uploadId, status)
                );
    }


    public Mono<Boolean> updateUploadStatus(String uploadId, String status) {
        return storeUploadStatus(uploadId, status);
    }

}
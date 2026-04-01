package kz.kaspi.fileupload.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "file_uploads")
public class FileUpload {

    @Id
    private String id;

    @Indexed
    private String originalFilename;

    private String contentType;

    private Long sizeBytes;

    @Indexed(unique = true)
    private String fileHash;

    private String storageUrl;

    @Indexed
    private UploadStatus status;

    @Indexed
    private String uploadedBy;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private String errorMessage;
}
package kz.kaspi.fileupload.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kz.kaspi.fileupload.domain.model.UploadStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed file upload status information")
public class FileStatusResponse {

    @Schema(description = "Upload identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private String uploadId;

    @Schema(description = "Original filename", example = "document.pdf")
    private String filename;

    @Schema(description = "Current status", example = "COMPLETED")
    private UploadStatus status;

    @Schema(description = "File size in bytes", example = "1024000")
    private Long sizeBytes;

    @Schema(description = "Storage URL (only if completed)", example = "minio://file-uploads/2026/01/31/file.pdf")
    private String storageUrl;

    @Schema(description = "Upload timestamp")
    private LocalDateTime uploadedAt;

    @Schema(description = "Error message (only if failed)")
    private String errorMessage;
}
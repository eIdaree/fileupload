package kz.kaspi.fileupload.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response after initiating file upload")
public class UploadResponse {

    @Schema(description = "Unique upload identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private String uploadId;

    @Schema(description = "Current upload status", example = "PROCESSING")
    private String status;

    @Schema(description = "Message describing the upload state", example = "Upload initiated successfully")
    private String message;
}
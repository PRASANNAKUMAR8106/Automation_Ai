package com.autoflow.modules.media.entity;

import com.autoflow.common.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "media_assets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaAsset extends TenantAwareEntity {

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "s3_bucket", nullable = false, length = 100)
    private String s3Bucket;

    @Column(name = "s3_key", nullable = false, unique = true, length = 500)
    private String s3Key;

    @Column(name = "sha256_checksum", length = 64)
    private String sha256Checksum;
}

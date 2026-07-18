package com.vikkash.assetmanagementv1.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Wires up the AWS SDK v2 {@link S3Client} (used for upload/download/delete)
 * and {@link S3Presigner} (used to generate time-limited, signed URLs) as
 * Spring beans, backed by the bucket/region/credentials configured in
 * application.properties (which in turn read from environment variables —
 * see {@code aws.s3.*} and {@code aws.accessKeyId} / {@code aws.secretKey}).
 *
 * Credentials are NEVER hardcoded here. Two supported modes:
 *   1. AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY (or aws.accessKeyId /
 *      aws.secretKey properties) are set  -> used directly (this is the
 *      normal path for Render, since Render has no concept of an EC2
 *      instance profile).
 *   2. Neither is set -> falls back to the AWS SDK's
 *      {@link DefaultCredentialsProvider} chain (environment variables with
 *      the SDK's own names, ~/.aws/credentials, EC2/ECS instance profile,
 *      etc.) — useful for local development with the AWS CLI already
 *      configured, or if this is ever deployed onto real AWS compute.
 */
@Configuration
public class S3Config {

    private static final Logger log = LoggerFactory.getLogger(S3Config.class);

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.accessKeyId:}")
    private String accessKeyId;

    @Value("${aws.secretKey:}")
    private String secretKey;

    private AwsCredentialsProvider credentialsProvider() {
        if (accessKeyId != null && !accessKeyId.isBlank() && secretKey != null && !secretKey.isBlank()) {
            log.info("S3Config: using static AWS credentials supplied via configuration/environment variables.");
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId.trim(), secretKey.trim()));
        }
        log.info("S3Config: no static AWS credentials configured — falling back to the AWS default credentials chain.");
        return DefaultCredentialsProvider.create();
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .build();
    }
}

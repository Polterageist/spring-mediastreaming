package com.example.demo

import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class ObjectStorageConfig {
    @Value("\${minio.url}")
    private lateinit var minioUrl: String

    @Value("\${minio.access-key}")
    private lateinit var minioAccessKey: String

    @Value("\${minio.secret-key}")
    private lateinit var minioSecretKey: String

    @Value("\${minio.bucket.stream}")
    lateinit var streamBucket: String

    private final val logger = LoggerFactory.getLogger(ObjectStorageConfig::class.java)

    @Bean
    fun minioClient(): MinioClient {
        val client = MinioClient.builder()
            .endpoint(minioUrl)
            .credentials(minioAccessKey, minioSecretKey)
            .build()

        initBucket(client, streamBucket)

        return client
    }

    private fun initBucket(minioClient: MinioClient, bucketName: String) {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build())
            logger.info("Bucket $bucketName created")
        }
    }
}
package io.odpf.firehose.sink.common.blobstorage;

import io.odpf.firehose.config.GCSConfig;
import io.odpf.firehose.sink.common.blobstorage.gcs.GoogleCloudStorage;
import org.aeonbits.owner.ConfigFactory;

import java.io.IOException;
import java.util.Map;

public class BlobStorageFactory {

    public static BlobStorage createObjectStorage(BlobStorageType storageType, Map<String, String> config) {
        if (storageType == BlobStorageType.GCS) {
            try {
                GCSConfig gcsConfig = ConfigFactory.create(GCSConfig.class, config);
                return new GoogleCloudStorage(gcsConfig);
            } catch (IOException e) {
                throw new IllegalArgumentException("Exception while creating GCS Storage", e);
            }
        }
        throw new IllegalArgumentException("Blob Storage Type " + storageType + " is not supported");
    }
}

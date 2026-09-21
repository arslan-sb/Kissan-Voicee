package com.kissanvoice.media;

import java.io.IOException;
import java.io.InputStream;

/**
 * Outbound port for audio storage.
 *
 * The domain never learns where media actually lives. Block 3 swaps the local
 * adapter for S3 by adding a second implementation - no service or controller
 * changes. Same pattern as the CRM port in the integration package.
 */
public interface MediaStoragePort {

    /** @return the storage key under which the object was written */
    String store(String key, InputStream content, long sizeBytes, String contentType) throws IOException;

    /** A URL the client can fetch the audio from, valid for a short period. */
    String presignedUrl(String key);
}

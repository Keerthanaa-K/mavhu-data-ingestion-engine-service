package com.mavhu.ingestion.service;

/**
 * An object location in S3, convertible to and from "s3://bucket/key".
 */
public record S3Location(String bucket, String key) {

    private static final String SCHEME = "s3://";

    public static S3Location parse(String s3Path) {
        if (s3Path == null || !s3Path.startsWith(SCHEME)) {
            throw new IllegalArgumentException("s3Path must look like s3://<bucket>/<key> but was: " + s3Path);
        }
        String rest = s3Path.substring(SCHEME.length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            throw new IllegalArgumentException("s3Path must look like s3://<bucket>/<key> but was: " + s3Path);
        }
        return new S3Location(rest.substring(0, slash), rest.substring(slash + 1));
    }

    @Override
    public String toString() {
        return SCHEME + bucket + "/" + key;
    }
}

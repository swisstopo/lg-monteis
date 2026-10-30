// spring.servlet.multipart.max-file-size of the backend, its UploadSizeLimitTest fails when the two
// differ
export const MAX_DOCUMENT_SIZE_MB = 50;
export const MAX_DOCUMENT_SIZE_BYTES = MAX_DOCUMENT_SIZE_MB * 1024 * 1024;

package dev.learning.property;

interface InfraiGateway {
    ModerationResult moderate(byte[] image, String contentType, String caption) throws Exception;
    String resize(byte[] image, String contentType, String requestId) throws Exception;

    record ModerationResult(boolean flagged) {}
}

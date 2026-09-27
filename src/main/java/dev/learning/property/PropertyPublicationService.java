package dev.learning.property;

import java.util.Locale;

final class PropertyPublicationService {
    enum SubmissionKind { MAINTENANCE_REQUEST, TENANT_DOCUMENT, INSPECTION_REMINDER }
    enum Decision { READY_TO_PUBLISH, QUARANTINED }
    record Submission(String requestId, SubmissionKind kind, String caption, String contentType, byte[] image) {}
    record Outcome(String requestId, Decision decision, String asset, String reason) {}

    private final InfraiGateway infrai;

    PropertyPublicationService(InfraiGateway infrai) { this.infrai = infrai; }

    Outcome screen(Submission submission) throws Exception {
        validate(submission);
        InfraiGateway.ModerationResult moderation = infrai.moderate(
                submission.image(), submission.contentType(), submission.caption());
        if (moderation.flagged()) {
            return new Outcome(submission.requestId(), Decision.QUARANTINED, null,
                    "Image or caption needs staff review");
        }
        String transformedAsset = infrai.resize(submission.image(), submission.contentType(), submission.requestId());
        return new Outcome(submission.requestId(), Decision.READY_TO_PUBLISH, transformedAsset,
                "Screened and resized for the property feed");
    }

    static SubmissionKind parseKind(String value) {
        return SubmissionKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private static void validate(Submission submission) {
        if (submission.requestId() == null || submission.requestId().isBlank()) throw new IllegalArgumentException("requestId is required");
        if (submission.caption() == null || submission.caption().isBlank()) throw new IllegalArgumentException("caption is required");
        if (submission.image() == null || submission.image().length == 0) throw new IllegalArgumentException("image body is required");
        if (!submission.contentType().startsWith("image/")) throw new IllegalArgumentException("Content-Type must be an image");
    }
}

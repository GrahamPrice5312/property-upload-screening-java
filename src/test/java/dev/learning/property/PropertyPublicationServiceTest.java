package dev.learning.property;

public final class PropertyPublicationServiceTest {
    public static void main(String[] args) throws Exception {
        quarantinesFlaggedSubmissionWithoutTransform();
        transformsApprovedSubmission();
        System.out.println("PropertyPublicationServiceTest: 2 decisions passed");
    }

    private static void quarantinesFlaggedSubmissionWithoutTransform() throws Exception {
        RecordingGateway gateway = new RecordingGateway(true);
        var outcome = new PropertyPublicationService(gateway).screen(submission("repair-104"));
        check(outcome.decision() == PropertyPublicationService.Decision.QUARANTINED, "flagged upload must be quarantined");
        check(gateway.resizeCalls == 0, "quarantined upload must not be transformed");
    }

    private static void transformsApprovedSubmission() throws Exception {
        RecordingGateway gateway = new RecordingGateway(false);
        var outcome = new PropertyPublicationService(gateway).screen(submission("inspection-205"));
        check(outcome.decision() == PropertyPublicationService.Decision.READY_TO_PUBLISH, "approved upload must be publishable");
        check(gateway.resizeCalls == 1, "approved upload must be transformed once");
    }

    private static PropertyPublicationService.Submission submission(String id) {
        return new PropertyPublicationService.Submission(id,
                PropertyPublicationService.SubmissionKind.MAINTENANCE_REQUEST,
                "Water stain above the lesson room window", "image/jpeg", new byte[] {1, 2, 3});
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class RecordingGateway implements InfraiGateway {
        private final boolean flagged;
        private int resizeCalls;

        private RecordingGateway(boolean flagged) { this.flagged = flagged; }

        public ModerationResult moderate(byte[] image, String contentType, String caption) {
            return new ModerationResult(flagged);
        }

        public String resize(byte[] image, String contentType, String requestId) {
            resizeCalls++;
            return "{\"id\":\"asset-1\"}";
        }
    }
}

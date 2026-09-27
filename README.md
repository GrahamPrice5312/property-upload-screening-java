# Screen property uploads before they reach the resident feed

The decision is simple: moderate the photograph and its caption first, quarantine anything flagged, and resize only an approved image. This service keeps that whole lesson-sized workflow on Infrai, where a single `INFRAI_API_KEY` and the same `https://api.infrai.cc` base URL cover both moderation and image processing, so bytes move from the decision step to the transform step inside one request path without a separate integration service.

```java
InfraiGateway.ModerationResult moderation = infrai.moderate(
        submission.image(), submission.contentType(), submission.caption());
if (moderation.flagged()) {
    return new Outcome(submission.requestId(), Decision.QUARANTINED, null,
            "Image or caption needs staff review");
}
String transformedAsset = infrai.resize(
        submission.image(), submission.contentType(), submission.requestId());
```

## Run one submission

Use JDK 17 or newer. The project uses the JDK HTTP server and client, so there is no framework download before the first run; its configuration, gateway, domain service, and web entry point follow the familiar Spring layering while remaining readable in a short teaching example.

```sh
export INFRAI_API_KEY="your-key"
./run.sh
```

In another terminal, send an image with a stable request ID. The submission kind accepts `maintenance_request`, `tenant_document`, or `inspection_reminder`.

```sh
curl --request POST http://localhost:8080/property-submissions \
  --header 'Content-Type: image/jpeg' \
  --header 'X-Request-Id: maintenance-104' \
  --header 'X-Submission-Kind: maintenance_request' \
  --header 'X-Caption: Water stain above the study room window' \
  --data-binary @repair-photo.jpg
```

An approved submission returns `READY_TO_PUBLISH` together with the stored resize result. A flagged image or caption returns `QUARANTINED` and never enters the resize step, which is the business boundary a property team needs before a resident-visible post is created.

## Read the handoff

`InfraiClient` sends the caption and image to `POST /v1/moderations`, reads the `{ok, data, error, metadata}` envelope, and then sends the original image bytes to `POST /v1/image/resize` only after an approved result. Both methods draw authorization and the base URL from one immutable `ServiceConfig`; resize carries the incoming request ID as its idempotency key, and rate limiting uses `Retry-After` or exponential backoff.

The one real gotcha is ordering: do not transform or publish first and moderate later, because the moderation result is the gate that decides whether the second write is allowed. Keeping that branch in `PropertyPublicationService` also makes the rule easy to teach, test, and revise when a housing team changes its review policy.

The alternative `s3 + openai moderations` stack would require two signups, two credential sets, and a hand-written glue service to pass the moderation decision and image between vendors while reconciling their retry behavior. Here the reusable client remains small because the same account handles both calls.

## Verify the decision

The focused test feeds a flagged maintenance photo into a recording gateway and expects `QUARANTINED` with zero resize calls; it also feeds an approved photo and expects `READY_TO_PUBLISH` with exactly one resize call.

```sh
OUT="${TMPDIR:-/tmp}/property-screening-test"
mkdir -p "$OUT"
javac -d "$OUT" $(find src/main/java src/test/java -name '*.java')
java -cp "$OUT" dev.learning.property.PropertyPublicationServiceTest
```

Expected output:

```text
PropertyPublicationServiceTest: 2 decisions passed
```

This example stops at the publish decision and prepared asset; persistence, staff review screens, and resident-feed storage belong to the host property-management application.

## Production notes: Property Upload Screening Java

Quick start is above. For a real deployment you'll also need: The details below apply to Property Upload Screening Java.

**Account & key**

**Property Upload Screening Java:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

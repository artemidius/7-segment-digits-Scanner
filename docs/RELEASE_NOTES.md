Initial public preview of the Kotlin/Android seven-segment detector runtime.

Two coroutine APIs: raw rows of strings (`Lines`) and detailed visual results
with field/character boxes and scores. The sample app supports gallery and camera.

Download the AAR for direct integration, or the Maven ZIP for dependency metadata.
See [integration instructions](https://github.com/artemidius/7-segment-digits-Scanner/blob/main/docs/INTEGRATION.md).
Check downloads against `SHA256SUMS`.

**Model required:** no weights are included. The optional SMART-BP research model
has separate AGPL terms, misses some small digits, and lacks punctuation classes.
This release does not claim complete-screen accuracy. The API may change during alpha.

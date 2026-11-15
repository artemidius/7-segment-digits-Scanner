# Contributing

Bug fixes, documentation improvements, and evidence-backed recognition improvements
are welcome. Start with an issue for significant API or model changes so we can
agree on scope before implementation.

1. Follow [development setup](docs/DEVELOPMENT.md).
2. Keep changes focused. Explain the user-visible behavior and compatibility impact.
3. Run relevant tests and lint. For runtime/model changes, run real-model device tests.
4. Include test results and limitations in the pull request.

Preserve coroutine cancellation, bitmap ownership, original-image coordinates, and
the simple `Lines` API. Do not infer missing digits or add medical interpretation
to the recognition engine. Public API changes need documentation and tests.

For model contributions, provide source, license, conversion details, and evaluation
on devices not used for training/tuning. Do not upload private medical data, model
weights with unclear redistribution rights, signing keys, or credentials.

Our code is Apache-2.0 licensed. Contributions are submitted under that license;
third-party material must retain its own license and attribution. Be respectful,
specific, and constructive in issues and reviews.

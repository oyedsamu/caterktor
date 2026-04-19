## Summary

<!-- What changed and why. Link the issue or the PRD-v2 section if relevant. -->

## Checklist

- [ ] Tests added (or N/A — explain below)
- [ ] `./gradlew apiCheck` passes; `./gradlew apiDump` run and the updated `.api` files committed if public API changed
- [ ] KDoc updated for any changed public surface
- [ ] No new `@Suppress` in public API
- [ ] Cancellation is not swallowed anywhere (never catch `CancellationException`)
- [ ] Breaking change? If yes, describe the migration in the PR body

## Notes for reviewer

<!-- Anything you would like the reviewer to focus on. -->

# Security Policy

## Reporting a vulnerability

Please report security vulnerabilities privately. Do **not** open a public
GitHub issue, discussion, or pull request for a suspected vulnerability.

You have two reporting channels; either is acceptable.

1. **GitHub private vulnerability reporting** (preferred):
   <https://github.com/oyedsamu/caterktor/security/advisories/new>
2. **Email**: `oyedsamu@gmail.com` with the subject line
   `[CaterKtor security]`.

When you report, please include:

- Affected versions (or commit SHA if reporting against `main`).
- A minimal reproducer, or a clear description of the attack scenario.
- Your assessment of the impact (what an attacker can do, under what
  preconditions).
- Any mitigations or workarounds you are aware of.

If you believe the issue is being actively exploited, say so in the
subject line.

## Disclosure timeline

- **Acknowledgment within 7 days** of the initial report.
- **Fix and coordinated disclosure within 90 days**, sooner if a patch is
  ready and the reporter agrees. If we need longer, we will tell you why
  and propose a revised date.
- **Credit** to the reporter in the advisory and release notes, unless
  the reporter asks to remain anonymous.

If we cannot reproduce the issue or we disagree that it is a
vulnerability, we will explain our reasoning in writing and keep the
channel open for rebuttal.

## Supported versions

Until the first public `0.1.0` release, **no versions are supported**.
All pre-`0.1.0` builds are development snapshots; do not deploy them to
production.

After `0.1.0`, the current minor and the immediately previous minor
receive security patches. Older minors do not.

| Version         | Supported |
| --------------- | --------- |
| `main` (pre-0.1.0) | No, development snapshot |
| Current minor   | Yes       |
| Previous minor  | Yes       |
| Older minors    | No        |

## Scope

This policy covers the CaterKtor artifacts published under the
`io.github.oyedsamu:caterktor-*` coordinates and the source in
<https://github.com/oyedsamu/caterktor>. Vulnerabilities in upstream
dependencies (Ktor, `kotlinx.serialization`, engine implementations,
etc.) should be reported to those projects directly; if a CaterKtor
default configuration makes such an issue materially worse, we still
want to hear about it.

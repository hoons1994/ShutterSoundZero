# Security Policy

Shutter Sound Zero handles Android Wireless Debugging, local ADB communication, system settings, and app-managed cryptographic material. Security reports should therefore be handled separately from ordinary bug reports.

## Supported versions

Security fixes are provided for the latest published release.

| Version | Supported |
| --- | --- |
| Latest published release | ✅ |
| Older releases | ❌ |

If a security issue also affects an older release, the fix may still be documented, but users should update to the latest release whenever possible.

## What should be reported as a security issue

Examples include, but are not limited to:

- Bypassing or abusing the Wireless Debugging pairing flow
- Unauthorized local ADB access or command execution
- Shell command injection or unsafe command construction
- Leakage, unintended export, or unsafe storage of app-managed RSA/private key material
- Unauthorized modification of Android secure/system settings
- Vulnerabilities involving exported activities, services, receivers, intents, or the Quick Settings tile
- Local-network behavior that exposes privileged app functionality to another device or process
- Backup, restore, logging, or diagnostics behavior that exposes credentials or cryptographic material
- Release-signing or GitHub Actions behavior that could expose signing credentials or produce an untrusted release

Ordinary crashes, UI problems, compatibility failures, or expected feature behavior without a security impact should use the regular bug report template instead.

## How to report a vulnerability

**Do not disclose vulnerability details in a public GitHub Issue, Pull Request, Discussion, commit message, or screenshot.**

Preferred reporting path:

1. Visit the repository Security page: https://github.com/hoons1994/ShutterSoundZero/security
2. If **Report a vulnerability** is available, use that private form to submit the report directly to the maintainer.
3. GitHub's official private-reporting instructions are available at https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/report-privately
4. If private vulnerability reporting is not available, open only a minimal public issue titled **`[Security contact request]`** asking for a private reporting channel.
5. Do not include technical details, proof-of-concept code, logs, screenshots, keys, credentials, pairing information, or exploit steps in that public request.

A useful private report should include, where applicable:

- A concise description of the issue and potential impact
- Affected Shutter Sound Zero version
- Galaxy model, Android version, and One UI version
- Reproduction steps using the minimum information necessary
- Whether physical access, Wireless Debugging, prior pairing, root, or a modified OS is required
- Any proof of concept that demonstrates the issue without unnecessarily accessing unrelated data
- Suggested mitigation or fix, if known

## Response and disclosure timeline

For a credible vulnerability report, the project aims to:

- acknowledge receipt within **7 days**;
- provide an initial assessment or request for additional information as soon as practical;
- provide a status update within **30 days** when investigation or remediation is still in progress; and
- coordinate public disclosure after a fix or suitable mitigation is available whenever reasonably possible.

These are target timelines rather than guarantees. Complex issues, platform dependencies, or coordinated disclosure with third parties may require more time. If a timeline changes materially, the reporter should be informed through the private reporting channel.

## Sensitive information

Never include any of the following in a public report:

- IMEI or device serial number
- Wi-Fi passwords or network credentials
- Wireless Debugging pairing codes or connection secrets
- Private keys, keystore files, signing certificates with private material, or GitHub Actions secrets
- Personal files, messages, photos, account tokens, or other unrelated user data

Please redact logs and screenshots before sharing them, even through a private report, unless the sensitive data is essential to understanding the vulnerability.

## Coordinated handling

Security reports that appear valid will be investigated privately. Additional information may be requested when needed to reproduce or assess the issue.

When appropriate, remediation may be developed through GitHub's private security advisory workflow. Public disclosure should occur only after a fix or suitable mitigation is available, unless there is a compelling reason to disclose earlier.

Please avoid testing that causes unnecessary data access, persistent device changes, service disruption, or impact to people or systems you do not own or have permission to test.

## Out of scope

The following are generally not treated as vulnerabilities unless they demonstrate a separate security boundary failure:

- Behavior that requires a rooted device, custom ROM, modified framework, or intentionally weakened Android security configuration
- Physical access to an already-unlocked device without bypassing an additional security boundary
- Unsupported non-Samsung devices or unsupported Android versions
- The intended ability of the app to change the configured shutter-sound-related system setting after the user completes the required setup
- Reports that only state that camera shutter sound can be disabled without demonstrating an additional vulnerability in Shutter Sound Zero

## Security-related development

Changes affecting ADB, pairing, cryptographic key handling, exported Android components, local-network access, release signing, or GitHub Actions secrets should receive extra review. Pull requests must continue to pass the repository's required Android CI, CodeQL, and Dependency Review checks before merge.

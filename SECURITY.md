# Security Policy

## Reporting a vulnerability

Please use GitHub's private vulnerability reporting or a private Security Advisory for this repository. Do not include access tokens, OAuth credentials, configuration files, or other secrets in a public issue.

Include the affected app version, proxy core version, Android version, reproduction steps, and the security impact. Reports involving upstream CLIProxyAPI behavior may also need to be coordinated with the upstream project.

## Supported builds

Security fixes target the latest signed Android release. Update APKs are accepted only when their package name, version code, SHA-256 digest, and signing certificate pass verification.

## Secret handling

Runtime credentials are stored in the app's private data directory. Release signing material must remain in GitHub Actions secrets or an encrypted offline backup and must never be committed to the repository.

# Security Policy

## Supported Versions

We release security updates for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 0.1.x   | :white_check_mark: |
| < 0.1   | :x:                |

## Reporting a Vulnerability

The master team takes security bugs seriously. We appreciate your efforts to responsibly disclose your findings, and will make every effort to acknowledge your contributions.

### How to Report

**Please do not report security vulnerabilities through public GitHub issues.**

Instead, please report them via one of the following methods:

1. **GitHub Security Advisories** (preferred): [Create a private security advisory](../../security/advisories/new)
2. **Email**: security@master-platform.dev (placeholder; real email set in v0.1.0 release)

Please include the following information in your report:

- **Type of issue** (e.g., buffer overflow, SQL injection, cross-site scripting, etc.)
- **Full paths of source file(s)** related to the manifestation of the issue
- **The location of the affected source code** (tag/branch/commit or direct URL)
- **Any special configuration** required to reproduce the issue
- **Step-by-step instructions** to reproduce the issue
- **Proof-of-concept or exploit code** (if possible)
- **Impact of the issue**, including how an attacker might exploit it

This information will help us triage your report more quickly.

### What to Expect

After you submit a report, you can expect:

1. **Acknowledgment** within 48 hours of submission
2. **Initial assessment** within 5 business days
3. **Regular updates** on progress (at least every 7 days)
4. **A coordinated disclosure timeline** agreed upon by both parties

We follow a **90-day disclosure timeline** (similar to Google Project Zero):

- Day 0: Vulnerability reported
- Day 0-7: Initial triage and confirmation
- Day 7-90: Patch development and testing
- Day 90: Public disclosure (or sooner if a fix is ready)

Critical issues (actively exploited, severe impact) may be disclosed and patched faster.

### What We Will Do

When we receive a security report, we will:

1. **Confirm receipt** and start investigation
2. **Determine severity** (using CVSS 3.1 scoring)
3. **Develop and test a fix** in a private fork
4. **Coordinate disclosure** with the reporter
5. **Release a security patch** and advisory
6. **Credit the reporter** (if desired) in the advisory

### Scope

The following are **in scope** for security reports:

- master backend (`master/` directory)
- master Web frontend (`web/` directory)
- worker (`worker/` directory)
- Documentation that leads to misconfiguration

The following are **out of scope**:

- Third-party dependencies (please report directly to upstream)
- Drone CI itself (report to https://github.com/harness/drone)
- Harbor itself (report to https://github.com/goharbor/harbor)
- Theoretical vulnerabilities without proof of concept
- Social engineering attacks
- Denial of service attacks

### Recognition

We appreciate security researchers who follow responsible disclosure. With your permission, we will:

- Credit you in the security advisory
- List you in our [Hall of Fame](../../security/advisories?state=published) (if you opt in)

## Security Best Practices for Self-Hosters

If you're running master in your own environment:

1. **Use HTTPS** for all master-to-drone and master-to-worker communication
2. **Rotate secrets regularly**: master encryption key, drone tokens, repo tokens, worker tokens
3. **Keep dependencies up to date**: enable Dependabot (already configured)
4. **Use a dedicated database user** with limited privileges (not root)
5. **Enable audit logging** for sensitive operations
6. **Network isolation**: workers should be in private subnets, not exposed to the internet
7. **Backup regularly**: master DB + Harbor + GitLab/Gitee are stateful
8. **Monitor**: enable Prometheus alerts on master, worker, drone metrics

## Security Architecture Notes

For reviewers and security researchers, here are the key security mechanisms in master:

### Authentication
- JWT-based auth (white-listed users in V1, RBAC in V2)
- HMAC-SHA256 for drone webhooks
- Token-based auth for master-to-worker

### Encryption
- AES-256-GCM for sensitive data at rest (env vars, repo tokens, webhook URLs)
- TLS for all network communication
- Envelope encryption designed (V1 with master key, V1.5+ with KMS/HashiCorp Vault)

### Input Validation
- All API inputs validated via Spring Boot Validation
- YAML parsed safely (snakeyaml with safe constructor)
- AI-generated YAML goes through schema validation + dangerous-string blacklist (rm -rf, curl|sh, chmod 777)
- SQL via MyBatis with parameterized queries (no string concatenation)

### Audit Logging
- All AI interactions logged in `ai_interaction` table (request/response/output action)
- All alert events logged with P0/P1/P2 level
- deploy_record tracks who triggered what, when

For full details, see [docs/superpowers/specs/2026-08-08-platform-design.md §7 Error Handling](docs/superpowers/specs/2026-08-08-platform-design.md#7-错误处理).

## Contact

For non-security issues, please use [GitHub Issues](../../issues) or [Discussions](../../discussions).

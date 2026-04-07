# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

We take the security of AEM Admin Tools seriously. If you believe you have found a security vulnerability, please report it to us as described below.

### Please Do

- **Report security vulnerabilities privately** by emailing security@example.com (replace with actual security contact)
- **Provide detailed information** about the vulnerability including:
  - Type of issue (e.g., buffer overflow, SQL injection, cross-site scripting)
  - Full paths of source file(s) related to the issue
  - Location of the affected source code (tag/branch/commit or direct URL)
  - Step-by-step instructions to reproduce the issue
  - Proof-of-concept or exploit code (if possible)
  - Impact of the issue, including how an attacker might exploit it
- **Allow time for response** - We will acknowledge your report within 48 hours

### Please Don't

- **Do not** open public issues for security vulnerabilities
- **Do not** disclose the vulnerability publicly until we have addressed it
- **Do not** access or modify other users' data without permission
- **Do not** perform actions that could harm the availability of the service

## Security Best Practices for Deployment

### Environment Variables

All sensitive configuration should be provided via environment variables:

```bash
# Required for production
export SECURITY_ENABLED=true
export SECURITY_ADMIN_PASSWORD=<strong-password>
export AEM_PASSWORD=<aem-password>
export LLM_OPENAI_API_KEY=<api-key>  # if using OpenAI
export LLM_ANTHROPIC_API_KEY=<api-key>  # if using Anthropic
```

### Network Security

1. **Use HTTPS** - Deploy behind a reverse proxy with TLS termination
2. **Firewall rules** - Restrict access to the admin tools to trusted networks
3. **VPN access** - Consider requiring VPN for production access

### Authentication

1. **Enable security** - Set `SECURITY_ENABLED=true` in production
2. **Strong passwords** - Use a strong admin password (minimum 16 characters)
3. **Rotate credentials** - Regularly rotate the admin password

### API Keys

1. **Never commit API keys** - Use environment variables or secret management
2. **Limit permissions** - Use API keys with minimal required permissions
3. **Rotate regularly** - Rotate API keys on a regular schedule

### Docker Security

1. **Non-root user** - Run containers as non-root (already configured)
2. **Read-only filesystem** - Consider using `--read-only` flag
3. **Resource limits** - Set CPU and memory limits
4. **Network isolation** - Use Docker networks to isolate services

### Monitoring

1. **Enable logging** - Set appropriate log levels for production
2. **Monitor access** - Review access logs regularly
3. **Alert on anomalies** - Set up alerts for suspicious activity

## Security Features

### Current Implementation

- [x] Environment variable configuration for secrets
- [x] Spring Security with Basic Authentication
- [x] Password encryption (BCrypt)
- [x] CORS configuration
- [x] Input validation
- [x] Global exception handling (no stack traces in responses)

### Planned Improvements

- [ ] OAuth2/OIDC integration
- [ ] JWT token authentication
- [ ] Rate limiting
- [ ] Audit logging
- [ ] IP allowlisting
- [ ] Two-factor authentication

## Acknowledgments

We appreciate responsible disclosure from the security community. Contributors who report valid security issues will be acknowledged here (with their permission).

# Security Policy

The crawler accepts network targets and processes untrusted remote content. SSRF bypasses, redirect-validation gaps, oversized-response handling, parser denial of service, credential exposure, and unsafe persistence behavior should be reported privately.

Use GitHub's **Security → Report a vulnerability** flow when available. Otherwise, contact the maintainer through the GitHub profile. Include a minimal local reproduction and do not probe third-party systems without permission.

The current `main` branch is supported. Deployments should still add authentication, TLS termination, network egress controls, and environment-specific rate limits.

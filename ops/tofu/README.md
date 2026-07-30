# DigitalOcean deployment

Creates one Ubuntu Droplet and its firewall, plus `app`, `auth`, and `grafana` DNS records in Cloudflare. Docker is installed with cloud-init; Ansible is intentionally unnecessary.

## Prerequisites

1. Create a Cloudflare API token scoped to `Zone:DNS:Edit` for the domain and copy its zone ID.
2. Create a private DigitalOcean Spaces bucket for OpenTofu state.
3. Register the deployment SSH public key with DigitalOcean.

```nu
$env.DIGITALOCEAN_TOKEN = "..."
$env.CLOUDFLARE_API_TOKEN = "..."
$env.AWS_ACCESS_KEY_ID = "..." # Spaces key
$env.AWS_SECRET_ACCESS_KEY = "..."
$env.TF_STATE_BUCKET = "..."
$env.TF_STATE_REGION = "sfo3"
$env.TF_VAR_domain = "example.com"
$env.TF_VAR_cloudflare_zone_id = "..."
$env.TF_VAR_ssh_key_fingerprint = "..."
^make up
```

DNS records default to Cloudflare DNS-only mode so Caddy can obtain certificates directly. Set `TF_VAR_cloudflare_proxied=true` (or `CLOUDFLARE_PROXIED=true` in GitHub) only after selecting Cloudflare SSL/TLS mode **Full (strict)**.

The default `s-4vcpu-8gb` size is the practical minimum for the application, Keycloak, two PostgreSQL instances, and the observability stack on one VM. SSH is key-only but open to all addresses so GitHub-hosted runners can deploy; set `TF_VAR_ssh_allowed_cidr` when using a fixed runner address.

Realm imports only configure a new Keycloak database. If the domain or client secrets change later, update the existing clients through Keycloak rather than expecting `--import-realm` to overwrite them.

## GitHub configuration

`deploy.yml` publishes an immutable Docker image, calls `provision.yml`, uploads the Compose configuration, and verifies the HTTPS health endpoint.

Repository variables:

```text
DOMAIN  CLOUDFLARE_ZONE_ID  CLOUDFLARE_PROXIED
DO_REGION  DO_SIZE  DO_SSH_KEY_FINGERPRINT
DO_SSH_ALLOWED_CIDR  SPACES_BUCKET  SPACES_REGION
DB_USERNAME  KEYCLOAK_DB_USERNAME  GRAFANA_ADMIN_USER
GOOGLE_CLIENT_ID  KEYCLOAK_BOOTSTRAP_ENABLED
```

Repository secrets used by the reusable publish/provision jobs:

```text
DIGITALOCEAN_TOKEN  CLOUDFLARE_API_TOKEN
SPACES_ACCESS_KEY  SPACES_SECRET_KEY
DOCKERHUB_USERNAME  DOCKERHUB_TOKEN
```

Production environment secrets:

```text
DEPLOY_SSH_PRIVATE_KEY  DB_PASSWORD  KEYCLOAK_DB_PASSWORD
KEYCLOAK_ADMIN_PASSWORD  KEYCLOAK_CLIENT_SECRET
KEYCLOAK_AUTOMATION_CLIENT_SECRET  KEYCLOAK_BOOTSTRAP_ADMIN_USER_PASSWORD
GRAFANA_ADMIN_PASSWORD  GOOGLE_CLIENT_SECRET  SLACK_WEBHOOK_URL
```

The SSH private key must match `DO_SSH_KEY_FINGERPRINT`. After the first successful bootstrap, set `KEYCLOAK_BOOTSTRAP_ENABLED=false` so the application no longer performs Keycloak administration during startup.

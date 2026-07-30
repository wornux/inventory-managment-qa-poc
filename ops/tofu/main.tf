terraform {
  required_version = ">= 1.11"

  required_providers {
    digitalocean = {
      source  = "digitalocean/digitalocean"
      version = "~> 2.0"
    }
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    key                         = "qa-final-project/tofu.tfstate"
    region                      = "us-east-1"
    use_lockfile                = true
    skip_credentials_validation = true
    skip_requesting_account_id  = true
    skip_metadata_api_check     = true
    skip_region_validation      = true
    skip_s3_checksum            = true
  }
}

variable "domain" {
  type = string
}

variable "ssh_key_fingerprint" {
  type = string
}

variable "cloudflare_zone_id" {
  type = string
}

variable "cloudflare_proxied" {
  type    = bool
  default = false
}

variable "region" {
  type    = string
  default = "nyc3"
}

variable "size" {
  type    = string
  default = "s-4vcpu-8gb"
}

variable "ssh_allowed_cidr" {
  type    = string
  default = "0.0.0.0/0"
}

resource "digitalocean_droplet" "app" {
  name       = "qa-final-project"
  image      = "ubuntu-24-04-x64"
  region     = var.region
  size       = var.size
  ssh_keys   = [var.ssh_key_fingerprint]
  user_data  = file("${path.module}/cloud-init.yaml")
  monitoring = true
  backups    = true
  tags       = ["qa-final-project"]
}

resource "digitalocean_firewall" "app" {
  name        = "qa-final-project"
  droplet_ids = [digitalocean_droplet.app.id]

  inbound_rule {
    protocol         = "tcp"
    port_range       = "22"
    source_addresses = [var.ssh_allowed_cidr]
  }

  inbound_rule {
    protocol         = "tcp"
    port_range       = "80"
    source_addresses = ["0.0.0.0/0", "::/0"]
  }

  inbound_rule {
    protocol         = "tcp"
    port_range       = "443"
    source_addresses = ["0.0.0.0/0", "::/0"]
  }

  inbound_rule {
    protocol         = "udp"
    port_range       = "443"
    source_addresses = ["0.0.0.0/0", "::/0"]
  }

  outbound_rule {
    protocol              = "tcp"
    port_range            = "all"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }

  outbound_rule {
    protocol              = "udp"
    port_range            = "all"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }

  outbound_rule {
    protocol              = "icmp"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }
}

resource "cloudflare_dns_record" "services" {
  for_each = toset(["app", "auth", "grafana"])

  zone_id = var.cloudflare_zone_id
  name    = "${each.value}.${var.domain}"
  type    = "A"
  content = digitalocean_droplet.app.ipv4_address
  ttl     = 1
  proxied = var.cloudflare_proxied
}

output "droplet_ip" {
  value = digitalocean_droplet.app.ipv4_address
}

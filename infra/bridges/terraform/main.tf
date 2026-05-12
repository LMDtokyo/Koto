terraform {
  required_version = ">= 1.6"
  required_providers {
    hcloud = {
      source  = "hetznercloud/hcloud"
      version = "~> 1.48"
    }
  }
}

# Token берётся из HCLOUD_TOKEN env-var. Не коммитить!
provider "hcloud" {}

# ─── SSH ─────────────────────────────────────────────────────────────────────
resource "hcloud_ssh_key" "operator" {
  name       = "koto-bridges-operator"
  public_key = file(var.ssh_public_key_path)
}

# ─── Network: один частный VPC под все бриджи ────────────────────────────────
resource "hcloud_network" "bridges" {
  name     = "koto-bridges"
  ip_range = "10.42.0.0/16"
}

resource "hcloud_network_subnet" "bridges" {
  network_id   = hcloud_network.bridges.id
  type         = "cloud"
  network_zone = "eu-central"
  ip_range     = "10.42.0.0/24"
}

# ─── Firewall: 443/tcp inbound, всё остальное закрыто ────────────────────────
resource "hcloud_firewall" "bridges" {
  name = "koto-bridges-fw"

  rule {
    direction = "in"
    protocol  = "tcp"
    port      = "22"
    source_ips = var.ssh_allowed_cidrs
  }

  rule {
    direction = "in"
    protocol  = "tcp"
    port      = "443"
    source_ips = ["0.0.0.0/0", "::/0"]
  }
}

# ─── Bridge-серверы (N штук, локации заданы в var.locations) ─────────────────
resource "hcloud_server" "bridge" {
  count       = length(var.locations)
  name        = "koto-bridge-${var.locations[count.index]}-${count.index + 1}"
  image       = "debian-12"
  server_type = var.server_type
  location    = var.locations[count.index]
  ssh_keys    = [hcloud_ssh_key.operator.id]

  firewall_ids = [hcloud_firewall.bridges.id]

  network {
    network_id = hcloud_network.bridges.id
  }

  # Минимальный bootstrap до Ansible — обновить пакеты + поставить python.
  user_data = <<-EOT
    #cloud-config
    package_update: true
    package_upgrade: true
    packages:
      - python3
      - curl
      - unzip
  EOT

  labels = {
    role = "koto-bridge"
    env  = "prod"
  }

  depends_on = [hcloud_network_subnet.bridges]
}

# ─── Сгенерированный inventory для Ansible ───────────────────────────────────
resource "local_file" "ansible_inventory" {
  filename = "${path.module}/../ansible/inventory.ini"
  content  = templatefile("${path.module}/inventory.tpl", {
    servers = [
      for s in hcloud_server.bridge : {
        name      = s.name
        public_ip = s.ipv4_address
        location  = s.location
      }
    ]
  })
}

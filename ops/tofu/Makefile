.PHONY: up init fmt validate

TF_STATE_REGION ?= nyc3

init:
	@test -n "$(TF_STATE_BUCKET)" || { echo "Set TF_STATE_BUCKET"; exit 2; }
	@printf 'bucket = "%s"\nendpoints = { s3 = "https://%s.digitaloceanspaces.com" }\n' "$(TF_STATE_BUCKET)" "$(TF_STATE_REGION)" > .backend.hcl
	tofu init -reconfigure -backend-config=.backend.hcl

up: init
	tofu apply $(TOFU_ARGS)

fmt:
	tofu fmt -check

validate: init
	tofu validate

.PHONY: jmeter-edit performance-test security-scan zap-security-test demo-alert

DEMO_ALERT_HOST ?= app.cristiandelahoz.dev
BREAKPOINT ?= false
BREAKPOINT_MAX_USERS ?= 500
# inventory-automation uses a 30-minute access token.
# Each automation run must still obtain a fresh token before starting.
BREAKPOINT_RAMP_SECONDS ?= 240
BREAKPOINT_DURATION_SECONDS ?= 270

security-scan:
	@test -f .env || { echo ".env not found. Copy .env.example to .env and configure NVD_API_KEY."; exit 1; }
	@NVD_API_KEY="$$(awk -F= '/^NVD_API_KEY=/{sub(/^[^=]*=/, ""); print; exit}' .env)"; \
	test -n "$$NVD_API_KEY" && test "$$NVD_API_KEY" != "change-me-nvd-api-key" \
		|| { echo "NVD_API_KEY is missing from .env or still has its example value."; exit 1; }; \
	NVD_API_KEY="$$NVD_API_KEY" ./mvnw -Psecurity-scan verify

zap-security-test:
	@./zap/run-authenticated-api-scan.sh

demo-alert:
	@ssh -o StrictHostKeyChecking=accept-new root@$(DEMO_ALERT_HOST) '\
		set -eu; \
		start=$$(date -u +%Y-%m-%dT%H:%M:%SZ); \
		end=$$(date -u -d "+2 minutes" +%Y-%m-%dT%H:%M:%SZ); \
		status=$$(printf '\''[{"labels":{"alertname":"DemoLowStock","severity":"warning","environment":"production","demo":"true"},"annotations":{"summary":"Demo: products are at or below minimum stock","description":"Synthetic low-stock alert triggered with make demo-alert."},"startsAt":"%s","endsAt":"%s"}]'\'' "$$start" "$$end" | curl -sS -o /dev/null -w "%{http_code}" -H "Content-Type: application/json" --data-binary @- http://127.0.0.1:9093/api/v2/alerts); \
		test "$$status" = 200; \
		echo "DemoLowStock sent; Slack notification follows after Alertmanager'\''s 10-second group wait."'

jmeter-edit:
	@command -v jmeter >/dev/null || { echo "JMeter is not installed. Run: brew install jmeter"; exit 1; }
	@AUTH_CLIENT_SECRET="$${KEYCLOAK_AUTOMATION_CLIENT_SECRET:-}" jmeter -t jmeter/load-and-stress-test.jmx \
		-Jhost=localhost -Jport=$${PORT:-8080} -JauthHost=localhost -JauthPort=7777

performance-test:
	@set -e; \
	case "$(BREAKPOINT)" in true|false) ;; *) echo "BREAKPOINT must be true or false"; exit 2;; esac; \
	if [ "$(BREAKPOINT)" = "true" ]; then \
		echo "Running linear breakpoint test up to $(BREAKPOINT_MAX_USERS) users"; \
		JMETER_STRESS_USERS="$${JMETER_STRESS_USERS:-$(BREAKPOINT_MAX_USERS)}" \
		JMETER_STRESS_RAMP_SECONDS="$${JMETER_STRESS_RAMP_SECONDS:-$(BREAKPOINT_RAMP_SECONDS)}" \
		JMETER_STRESS_DURATION_SECONDS="$${JMETER_STRESS_DURATION_SECONDS:-$(BREAKPOINT_DURATION_SECONDS)}" \
			docker compose --profile performance run --rm jmeter; \
	else \
		docker compose --profile performance run --rm jmeter; \
	fi

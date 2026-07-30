.PHONY: jmeter-edit performance-test zap-security-test

BREAKPOINT ?= false
BREAKPOINT_MAX_USERS ?= 500
# inventory-automation uses a 30-minute access token.
# Each automation run must still obtain a fresh token before starting.
BREAKPOINT_RAMP_SECONDS ?= 240
BREAKPOINT_DURATION_SECONDS ?= 270

zap-security-test:
	@./zap/run-authenticated-api-scan.sh

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

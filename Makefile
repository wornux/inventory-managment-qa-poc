.PHONY: jmeter-edit performance-test

jmeter-edit:
	@command -v jmeter >/dev/null || { echo "JMeter is not installed. Run: brew install jmeter"; exit 1; }
	jmeter -t telemetry/jmeter/load-and-stress-test.jmx -Jhost=localhost -Jport=$${PORT:-8080}

performance-test:
	docker compose --profile performance run --rm jmeter

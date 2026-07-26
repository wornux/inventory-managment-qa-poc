import http from 'k6/http';
import { check } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '1m', target: 10 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  const response = http.get(`${__ENV.BASE_URL || 'http://host.docker.internal:8080'}/actuator/health`);
  check(response, { 'health endpoint responds': (result) => result.status === 200 });
}

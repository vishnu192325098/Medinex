"""
locustfile.py - Medinex Performance & Load Testing with Locust
Baseline profile: 100 concurrent virtual users simulating healthcare app traffic.
"""

from locust import HttpUser, task, between

class MedinexUser(HttpUser):
    wait_time = between(0.5, 2.0)

    @task(5)
    def test_health(self):
        self.client.get("/api/health")

    @task(2)
    def test_root(self):
        self.client.get("/")

INSERT INTO agents (id, name, hostname, environment, region, registered_at, last_heartbeat_at)
VALUES
    ('8b0192ec-76e5-4e69-9d4f-2dcb8f4c4f31', 'edge-us-east-1', 'edge-gateway-01', 'staging', 'us-east-1', NOW(), NOW()),
    ('ee42c846-bd49-45d0-bd72-0ccf86372dc8', 'payments-eu-west-1', 'payments-worker-02', 'staging', 'eu-west-1', NOW(), NOW()),
    ('2172f6bb-76c1-44ae-8042-ef8a4bc591a5', 'inventory-us-west-2', 'inventory-daemon-01', 'dev', 'us-west-2', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO agent_capabilities (agent_id, capability)
VALUES
    ('8b0192ec-76e5-4e69-9d4f-2dcb8f4c4f31', 'http_error'),
    ('8b0192ec-76e5-4e69-9d4f-2dcb8f4c4f31', 'latency'),
    ('ee42c846-bd49-45d0-bd72-0ccf86372dc8', 'latency'),
    ('ee42c846-bd49-45d0-bd72-0ccf86372dc8', 'packet_loss'),
    ('2172f6bb-76c1-44ae-8042-ef8a4bc591a5', 'cpu_pressure'),
    ('2172f6bb-76c1-44ae-8042-ef8a4bc591a5', 'http_error')
ON CONFLICT (agent_id, capability) DO NOTHING;

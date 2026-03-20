# Complete Kafka Learning Guide - From Basics to Advanced

## 📚 Table of Contents

1. [What is Kafka?](#1-what-is-kafka)
2. [Core Concepts Explained](#2-core-concepts-explained)
3. [Partitions Deep Dive](#3-partitions-deep-dive)
4. [Offsets Explained](#4-offsets-explained)
5. [Topics in Detail](#5-topics-in-detail)
6. [Producers](#6-producers)
7. [Consumers and Consumer Groups](#7-consumers-and-consumer-groups)
8. [Practical Examples from Our System](#8-practical-examples-from-our-system)
9. [Best Practices](#9-best-practices)
10. [Common Scenarios](#10-common-scenarios)
11. [Troubleshooting](#11-troubleshooting)
12. [Learning Path](#12-learning-path)

---

## 1. What is Kafka?

### Simple Explanation
Kafka is like a **super-fast, reliable postal service** for data:
- **Producers** are like people sending letters (messages)
- **Topics** are like different mailboxes (categories)
- **Consumers** are like people reading letters
- **Partitions** are like multiple slots in a mailbox (for parallel processing)
- **Offsets** are like page numbers (to track what you've read)

### Real-World Analogy
Think of Kafka as a **highway system**:
- **Topics** = Different highways (e.g., "orders-highway", "payments-highway")
- **Partitions** = Lanes on each highway (more lanes = more traffic capacity)
- **Messages** = Cars traveling on the highway
- **Producers** = On-ramps (where cars enter)
- **Consumers** = Off-ramps (where cars exit)
- **Offsets** = Mile markers (to know your position)

### Why Use Kafka?
✅ **High Throughput** - Handle millions of messages per second  
✅ **Scalability** - Add more partitions/brokers as needed  
✅ **Durability** - Messages are persisted to disk  
✅ **Fault Tolerance** - Data is replicated across brokers  
✅ **Real-Time** - Low latency (milliseconds)  
✅ **Decoupling** - Producers and consumers are independent  

---

## 2. Core Concepts Explained

### 2.1 Message (Record)
The basic unit of data in Kafka.

**Structure:**
```
Message = Key + Value + Timestamp + Headers
```

**Example from Our System:**
```json
Key: {
  "orderId": "ORD-12345",
  "customerId": "CUST-67890"
}

Value: {
  "active": true,
  "amount": 1234.56,
  "tradeDate": "2026-03-20",
  "status": "CONFIRMED"
}

Timestamp: 1773943544591
Headers: {
  "source": "web-portal",
  "version": "2.0"
}
```

**Key Points:**
- **Key**: Used for partitioning (messages with same key go to same partition)
- **Value**: The actual data payload
- **Timestamp**: When the message was created
- **Headers**: Metadata (optional)

---

### 2.2 Topic
A category or feed name where messages are published.

**Analogy:** Topic = Database Table or Message Queue

**Example Topics:**
- `orders` - All order-related messages
- `payments` - All payment transactions
- `user-activity` - User behavior events
- `test-topic` - Our working topic in the system

**Topic Naming Best Practices:**
```
✅ Good: orders, user-events, payment-transactions
✅ Good: app.orders.created, app.orders.updated
❌ Bad: topic1, data, test
❌ Bad: OrdersTopicForProduction (too verbose)
```

---

### 2.3 Partition
A topic is divided into partitions for parallelism and scalability.

**Visual Representation:**
```
Topic: orders (3 partitions)

Partition 0: [msg1] [msg2] [msg3] [msg4] ...
Partition 1: [msg5] [msg6] [msg7] [msg8] ...
Partition 2: [msg9] [msg10] [msg11] [msg12] ...
```

**Why Partitions?**
1. **Parallelism** - Multiple consumers can read simultaneously
2. **Scalability** - Distribute load across brokers
3. **Ordering** - Messages in same partition are ordered
4. **Performance** - Higher throughput

**Key Rules:**
- Messages with **same key** → **same partition** (guaranteed)
- Messages with **no key** → **round-robin** distribution
- **Order is guaranteed** only within a partition
- **Cannot reduce** partition count (only increase)

---

### 2.4 Offset
A unique identifier for each message within a partition.

**Visual:**
```
Partition 0:
Offset: 0    1    2    3    4    5    6    7
        [A]  [B]  [C]  [D]  [E]  [F]  [G]  [H]
         ↑                   ↑              ↑
    First message    Current consumer   Latest message
```

**Types of Offsets:**
1. **Current Offset** - Where consumer is currently reading
2. **Committed Offset** - Last successfully processed offset
3. **Log End Offset** - Latest message in partition
4. **Lag** - Difference between current and log end offset

**Example from Our System:**
```json
{
  "partition": 0,
  "currentOffset": 5,
  "logEndOffset": 5,
  "lag": 0
}
```

---

### 2.5 Broker
A Kafka server that stores data and serves clients.

**Cluster Example:**
```
Kafka Cluster (3 brokers)

Broker 1 (Leader for P0)     Broker 2 (Leader for P1)     Broker 3 (Leader for P2)
- Topic: orders, P0           - Topic: orders, P1           - Topic: orders, P2
- Replica: orders, P1         - Replica: orders, P2         - Replica: orders, P0
```

**Our System:**
- **1 Broker** (localhost:9092)
- **KRaft mode** (no ZooKeeper)
- **PLAINTEXT** protocol (no encryption for local dev)

---

### 2.6 Consumer Group
A group of consumers working together to consume a topic.

**Visual:**
```
Topic: orders (3 partitions)

Consumer Group: order-processors
├── Consumer 1 → Partition 0
├── Consumer 2 → Partition 1
└── Consumer 3 → Partition 2

Each consumer reads from different partition = Parallel processing
```

**Key Rules:**
- **One partition** → **One consumer** in a group (at a time)
- **Multiple groups** can read the **same topic** independently
- **Rebalancing** happens when consumers join/leave

**Our System:**
- Consumer Group: `test-group`
- Consuming from: `test-topic`
- Status: `STABLE`

---

## 3. Partitions Deep Dive

### 3.1 How Partitioning Works

**With Key (Deterministic):**
```java
partition = hash(key) % number_of_partitions

Example:
key = "ORD-12345"
hash(key) = 98765
partitions = 3
partition = 98765 % 3 = 0

Result: Message goes to Partition 0
```

**Without Key (Round-Robin):**
```
Message 1 → Partition 0
Message 2 → Partition 1
Message 3 → Partition 2
Message 4 → Partition 0
...
```

### 3.2 Partition Distribution

**Example: 3 Partitions, 3 Brokers**
```
Broker 1:
- orders-0 (Leader)
- orders-1 (Replica)

Broker 2:
- orders-1 (Leader)
- orders-2 (Replica)

Broker 3:
- orders-2 (Leader)
- orders-0 (Replica)
```

### 3.3 Choosing Partition Count

**Formula:**
```
Partitions = (Target Throughput) / (Consumer Throughput)

Example:
Target: 1000 messages/sec
Consumer can process: 100 messages/sec
Partitions needed: 1000 / 100 = 10 partitions
```

**Guidelines:**
- **Start small** (3-6 partitions)
- **Monitor** throughput and lag
- **Scale up** as needed
- **Consider** number of consumers

**Our System:**
- Topic: `test-topic`
- Partitions: **1** (sufficient for development)

---

## 4. Offsets Explained

### 4.1 Offset Management

**Automatic (Default):**
```yaml
enable.auto.commit: true
auto.commit.interval.ms: 5000

# Commits offset every 5 seconds automatically
```

**Manual (More Control):**
```java
consumer.commitSync();  // Commit immediately
consumer.commitAsync(); // Commit in background
```

### 4.2 Offset Reset Strategies

**Configuration:**
```yaml
auto.offset.reset: earliest | latest | none

earliest: Start from beginning
latest: Start from newest messages (default)
none: Throw exception if no offset found
```

**Visual:**
```
Partition: [0] [1] [2] [3] [4] [5] [6] [7]
            ↑                           ↑
        earliest                    latest

New consumer with:
- earliest → starts at offset 0
- latest → starts at offset 8 (next message)
```

### 4.3 Consumer Lag

**Definition:**
```
Lag = Log End Offset - Current Offset

Example:
Log End Offset: 1000
Current Offset: 950
Lag: 50 messages behind
```

**Monitoring Lag (Our System):**
```bash
GET /api/monitoring/consumer-groups/test-group/lag

Response:
{
  "totalLag": 0,
  "status": "HEALTHY",
  "message": "Consumer is up to date"
}
```

**Lag Status:**
- **0-10**: HEALTHY ✅
- **10-100**: GOOD 🟢
- **100-1000**: WARNING ⚠️
- **1000+**: CRITICAL 🔴

---

## 5. Topics in Detail

### 5.1 Creating Topics

**Method 1: Auto-Create (Not Recommended for Production)**
```yaml
auto.create.topics.enable: true

# Topic created automatically when producer sends first message
```

**Method 2: Manual Creation (Recommended)**
```bash
# Create topic with 3 partitions, replication factor 2
kafka-topics --bootstrap-server localhost:9092 \
  --create \
  --topic orders \
  --partitions 3 \
  --replication-factor 2
```

**Method 3: Admin API (Programmatic)**
```java
AdminClient admin = AdminClient.create(props);
NewTopic topic = new NewTopic("orders", 3, (short) 2);
admin.createTopics(Collections.singleton(topic));
```

### 5.2 Topic Configuration

**Important Settings:**
```yaml
# Retention (how long to keep messages)
retention.ms: 604800000  # 7 days (default)
retention.bytes: -1      # Unlimited (default)

# Cleanup policy
cleanup.policy: delete   # Delete old messages
cleanup.policy: compact  # Keep only latest per key

# Segment size
segment.bytes: 1073741824  # 1 GB

# Compression
compression.type: none | gzip | snappy | lz4 | zstd
```

### 5.3 Topic Naming Conventions

**Best Practices:**
```
✅ app.domain.entity.action
   └─ payments.orders.created
   └─ payments.orders.updated
   └─ analytics.user.clicked

✅ environment.app.entity
   └─ prod.orders.events
   └─ dev.orders.events

❌ Avoid:
   - Special characters (except . - _)
   - Spaces
   - Very long names (>249 characters)
```

---

## 6. Producers

### 6.1 Producer Workflow

```
1. Producer creates message
2. Serializer converts to bytes
3. Partitioner determines partition
4. Message added to batch
5. Batch sent to broker
6. Broker writes to partition
7. Acknowledgment sent back
```

### 6.2 Producer Configuration

**Key Settings:**
```yaml
# Acknowledgment
acks: 0 | 1 | all
  0: No acknowledgment (fastest, least safe)
  1: Leader acknowledgment (balanced)
  all: All replicas acknowledgment (slowest, safest)

# Retries
retries: 2147483647  # Max retries
retry.backoff.ms: 100

# Batching
batch.size: 16384  # 16 KB
linger.ms: 0       # Send immediately (0) or wait (>0)

# Compression
compression.type: none | gzip | snappy | lz4 | zstd

# Idempotence (prevent duplicates)
enable.idempotence: true
```

**Our System Configuration:**
```yaml
spring:
  kafka:
    producer:
      key-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
      properties:
        schema.registry.url: http://localhost:8081
        auto.register.schemas: false
```

### 6.3 Producer Example

**From Our System:**
```java
// 1. Create message
EventRequest request = new EventRequest();
request.setTopic("test-topic");
request.setContractDataKey(keyJson);
request.setPayload(valueJson);

// 2. Producer sends
ProducerRecord<GenericRecord, GenericRecord> record = 
    new ProducerRecord<>(topic, key, value);

// 3. Send and get metadata
RecordMetadata metadata = producer.send(record).get();

// 4. Metadata contains:
metadata.topic();      // "test-topic"
metadata.partition();  // 0
metadata.offset();     // 5
metadata.timestamp();  // 1773943544591
```

---

## 7. Consumers and Consumer Groups

### 7.1 Consumer Workflow

```
1. Consumer subscribes to topic
2. Partition assignment (rebalancing)
3. Fetch messages from assigned partitions
4. Deserialize messages
5. Process messages
6. Commit offsets
7. Repeat from step 3
```

### 7.2 Consumer Configuration

**Key Settings:**
```yaml
# Group
group.id: my-consumer-group  # REQUIRED

# Offset management
enable.auto.commit: true
auto.commit.interval.ms: 5000
auto.offset.reset: latest | earliest | none

# Fetch settings
fetch.min.bytes: 1
fetch.max.wait.ms: 500
max.partition.fetch.bytes: 1048576  # 1 MB

# Session timeout
session.timeout.ms: 10000
heartbeat.interval.ms: 3000

# Max records per poll
max.poll.records: 500
max.poll.interval.ms: 300000  # 5 minutes
```

**Our System Configuration:**
```yaml
spring:
  kafka:
    consumer:
      group-id: test-group
      auto-offset-reset: earliest
      key-deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      value-deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      properties:
        schema.registry.url: http://localhost:8081
        specific.avro.reader: false
        use.latest.version: true
```

### 7.3 Consumer Groups in Action

**Scenario 1: More Consumers than Partitions**
```
Topic: orders (3 partitions)
Consumer Group: processors (5 consumers)

Result:
Consumer 1 → Partition 0
Consumer 2 → Partition 1
Consumer 3 → Partition 2
Consumer 4 → IDLE (no partition assigned)
Consumer 5 → IDLE (no partition assigned)
```

**Scenario 2: More Partitions than Consumers**
```
Topic: orders (6 partitions)
Consumer Group: processors (2 consumers)

Result:
Consumer 1 → Partitions 0, 1, 2
Consumer 2 → Partitions 3, 4, 5
```

**Scenario 3: Multiple Consumer Groups**
```
Topic: orders (3 partitions)

Group 1: order-processors
├── Consumer 1 → Partition 0
├── Consumer 2 → Partition 1
└── Consumer 3 → Partition 2

Group 2: analytics
├── Consumer 1 → Partition 0
├── Consumer 2 → Partition 1
└── Consumer 3 → Partition 2

Both groups read ALL messages independently!
```

### 7.4 Rebalancing

**When Rebalancing Happens:**
1. Consumer joins the group
2. Consumer leaves the group
3. Consumer crashes
4. New partitions added to topic

**Rebalancing Process:**
```
1. Stop consuming
2. Revoke partitions
3. Assign new partitions
4. Resume consuming

Duration: 1-10 seconds (during this time, no messages processed)
```

**Strategies:**
- **Range** (default): Assign contiguous partitions
- **Round-Robin**: Distribute evenly
- **Sticky**: Minimize partition movement
- **Cooperative Sticky**: Incremental rebalancing (Kafka 2.4+)

---

## 8. Practical Examples from Our System

### 8.1 Current Setup

**Infrastructure:**
```
Kafka Broker: localhost:9092 (1 broker)
Schema Registry: localhost:8081
Application: localhost:8080

Topic: test-topic
- Partitions: 1
- Replication: 1
- Messages: 5

Consumer Group: test-group
- Consumers: 1
- Status: STABLE
- Lag: 0
```

### 8.2 Publishing a Message

**API Call:**
```bash
POST http://localhost:8080/api/events

{
  "topic": "test-topic",
  "contractDataKey": {
    "orderId": "ORD-12345",
    "customerId": "CUST-67890"
  },
  "payload": {
    "active": true,
    "amount": 1234.56,
    "tradeDate": "2026-03-20",
    "status": "CONFIRMED"
  }
}
```

**What Happens:**
```
1. REST API receives request
2. GenericRecordBuilder creates Avro records
3. Schema fetched from Registry (ID: 3 for key, 4 for value)
4. Producer serializes with Avro
5. Message sent to Kafka
6. Kafka writes to partition 0
7. Offset assigned (e.g., 5)
8. Response returned to client
```

### 8.3 Consuming Messages

**Consumer Process:**
```
1. Consumer subscribes to test-topic
2. Assigned partition 0
3. Polls for messages
4. Deserializes with Avro
5. Extracts fields using SafeFieldExtractor
6. Logs message details
7. Commits offset
```

**Consumer Log Output:**
```
Received message:
  Topic: test-topic
  Partition: 0
  Offset: 5
  Key: orderId=ORD-12345, customerId=CUST-67890
  Value: active=true, amount=1234.56, status=CONFIRMED
```

### 8.4 Monitoring with Our APIs

**Check Consumer Lag:**
```bash
GET /api/monitoring/consumer-groups/test-group/lag

{
  "groupId": "test-group",
  "totalLag": 0,
  "status": "HEALTHY",
  "partitions": [
    {
      "partition": 0,
      "currentOffset": 5,
      "logEndOffset": 5,
      "lag": 0
    }
  ]
}
```

**Preview Messages:**
```bash
GET /api/preview/topics/test-topic/messages?limit=5

{
  "topic": "test-topic",
  "totalMessages": 5,
  "messages": [
    {"offset": 0, "keySize": 13, "valueSize": 13},
    {"offset": 1, "keySize": 27, "valueSize": 173},
    ...
  ]
}
```

---

## 9. Best Practices

### 9.1 Topic Design

✅ **DO:**
- Use meaningful names
- Plan partition count based on throughput
- Set appropriate retention
- Use compression for large messages
- Document topic purpose and schema

❌ **DON'T:**
- Create too many topics (overhead)
- Use very few partitions (bottleneck)
- Store large files in messages (>1MB)
- Change partition count frequently
- Delete topics in production without backup

### 9.2 Producer Best Practices

✅ **DO:**
```java
// Use keys for ordering
producer.send(new ProducerRecord<>(topic, key, value));

// Enable idempotence
props.put("enable.idempotence", true);

// Handle errors
producer.send(record, (metadata, exception) -> {
    if (exception != null) {
        log.error("Failed to send", exception);
    }
});

// Close producer properly
producer.close();
```

❌ **DON'T:**
```java
// Don't ignore errors
producer.send(record);  // Fire and forget

// Don't create new producer per message
for (Message msg : messages) {
    KafkaProducer producer = new KafkaProducer(props);  // BAD!
    producer.send(msg);
}
```

### 9.3 Consumer Best Practices

✅ **DO:**
```java
// Process in batches
List<ConsumerRecord> records = consumer.poll(Duration.ofMillis(100));
for (ConsumerRecord record : records) {
    process(record);
}
consumer.commitSync();

// Handle errors gracefully
try {
    process(record);
} catch (Exception e) {
    sendToDLT(record);  // Dead Letter Topic
}

// Close consumer properly
consumer.close();
```

❌ **DON'T:**
```java
// Don't commit after each message (slow)
for (ConsumerRecord record : records) {
    process(record);
    consumer.commitSync();  // BAD!
}

// Don't process too long (causes rebalancing)
for (ConsumerRecord record : records) {
    processForHours(record);  // BAD!
}
```

### 9.4 Performance Tuning

**For High Throughput:**
```yaml
# Producer
batch.size: 32768  # Larger batches
linger.ms: 10      # Wait for more messages
compression.type: lz4  # Compress
buffer.memory: 67108864  # 64 MB buffer

# Consumer
fetch.min.bytes: 50000  # Fetch more data
max.poll.records: 1000  # Process more per poll
```

**For Low Latency:**
```yaml
# Producer
linger.ms: 0  # Send immediately
batch.size: 0  # No batching

# Consumer
fetch.min.bytes: 1  # Don't wait
max.poll.records: 100  # Process fewer per poll
```

---

## 10. Common Scenarios

### 10.1 Scenario: Order Processing System

**Requirements:**
- Process orders in sequence per customer
- High throughput (10,000 orders/sec)
- Multiple processing services

**Solution:**
```
Topic: orders
Partitions: 20 (10,000 / 500 per partition)
Key: customerId (ensures order for same customer)
Consumer Group: order-processors (20 consumers)

Result:
- Each customer's orders processed in sequence
- 20 consumers process in parallel
- 500 orders/sec per consumer
```

### 10.2 Scenario: Real-Time Analytics

**Requirements:**
- Multiple teams need same data
- Real-time processing
- Historical replay capability

**Solution:**
```
Topic: user-events
Partitions: 10
Retention: 30 days

Consumer Group 1: real-time-dashboard
Consumer Group 2: ml-training
Consumer Group 3: audit-logging

Result:
- Each group reads independently
- Can replay last 30 days
- Real-time processing
```

### 10.3 Scenario: Failed Message Handling

**Requirements:**
- Retry failed messages
- Don't block processing
- Track failures

**Solution:**
```
Main Topic: orders
DLT Topic: orders.DLT

Process:
1. Try to process message
2. If fails after 3 retries → Send to DLT
3. Continue processing other messages
4. Inspect DLT periodically
5. Fix issues and replay

Our System API:
GET /api/dlt/orders/messages
```

---

## 11. Troubleshooting

### 11.1 Consumer Lag Issues

**Problem:** Consumer falling behind

**Diagnosis:**
```bash
GET /api/monitoring/consumer-groups/test-group/lag

{
  "totalLag": 5000,
  "status": "CRITICAL"
}
```

**Solutions:**
1. **Add more consumers** (up to partition count)
2. **Increase partitions** (requires topic recreation)
3. **Optimize processing** (batch operations)
4. **Scale vertically** (more CPU/memory)

### 11.2 Rebalancing Too Often

**Problem:** Frequent rebalancing causing downtime

**Diagnosis:**
```
Consumer logs:
"Revoking previously assigned partitions"
"Partitions assigned"
```

**Solutions:**
```yaml
# Increase timeouts
session.timeout.ms: 30000  # 30 seconds
max.poll.interval.ms: 600000  # 10 minutes

# Reduce processing time per poll
max.poll.records: 100  # Process fewer messages
```

### 11.3 Message Loss

**Problem:** Messages disappearing

**Possible Causes:**
1. **Producer acks=0** → No acknowledgment
2. **Retention too short** → Messages deleted
3. **Consumer auto-commit** → Offset committed before processing

**Solutions:**
```yaml
# Producer
acks: all  # Wait for all replicas
enable.idempotence: true

# Consumer
enable.auto.commit: false  # Manual commit
# Commit only after successful processing
```

### 11.4 Duplicate Messages

**Problem:** Same message processed multiple times

**Causes:**
1. Consumer crashes before committing offset
2. Rebalancing during processing
3. Producer retries

**Solutions:**
```java
// 1. Enable idempotence (producer)
props.put("enable.idempotence", true);

// 2. Use exactly-once semantics
props.put("isolation.level", "read_committed");

// 3. Implement idempotent processing (consumer)
if (!isProcessed(messageId)) {
    process(message);
    markAsProcessed(messageId);
}
```

---

## 12. Learning Path

### Phase 1: Basics (Week 1)
✅ Understand core concepts (topics, partitions, offsets)  
✅ Set up local Kafka (Docker)  
✅ Create simple producer and consumer  
✅ Experiment with our working system  

**Hands-On:**
```bash
# 1. Start our system
docker-compose up -d
mvn spring-boot:run

# 2. Publish message via Swagger UI
http://localhost:8080/swagger-ui/index.html#/

# 3. Check consumer logs
# 4. Monitor with our APIs
GET /api/kafka/topics
GET /api/monitoring/consumer-groups/test-group/lag
```

### Phase 2: Intermediate (Week 2-3)
✅ Deep dive into partitioning strategies  
✅ Understand consumer groups and rebalancing  
✅ Learn offset management  
✅ Implement error handling (DLT)  

**Hands-On:**
```bash
# 1. Compare schemas
POST /api/schema/compare

# 2. Test compatibility
POST /api/schema/test-compatibility

# 3. Preview messages
GET /api/preview/topics/test-topic/messages

# 4. Inspect DLT
GET /api/dlt/test-topic/messages
```

### Phase 3: Advanced (Week 4+)
✅ Performance tuning  
✅ Schema evolution  
✅ Monitoring and alerting  
✅ Production deployment  

**Hands-On:**
```bash
# 1. Validate bulk data
POST /api/validation/validate-batch

# 2. Monitor lag continuously
GET /api/monitoring/consumer-groups/test-group/lag

# 3. Test schema changes
POST /api/schema/test-compatibility
```

---

## 📚 Additional Resources

### Official Documentation
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Confluent Documentation](https://docs.confluent.io/)

### Books
- "Kafka: The Definitive Guide" by Neha Narkhede
- "Kafka Streams in Action" by William Bejeck

### Our System APIs
All 27 APIs available at: http://localhost:8080/swagger-ui/index.html#/

### Practice Projects
1. Build a real-time chat application
2. Create an event-driven microservices system
3. Implement a data pipeline with Kafka Streams
4. Build a CDC (Change Data Capture) system

---

## 🎯 Quick Reference

### Key Kafka Variables

```yaml
# Broker
bootstrap.servers: localhost:9092
broker.id: 0

# Topic
num.partitions: 1
replication.factor: 1
retention.ms: 604800000  # 7 days
segment.bytes: 1073741824  # 1 GB

# Producer
acks: all
retries: 2147483647
batch.size: 16384
linger.ms: 0
compression.type: none
enable.idempotence: true

# Consumer
group.id: my-group
auto.offset.reset: latest
enable.auto.commit: true
auto.commit.interval.ms: 5000
max.poll.records: 500
session.timeout.ms: 10000
```

### Common Commands

```bash
# List topics
kafka-topics --bootstrap-server localhost:9092 --list

# Describe topic
kafka-topics --bootstrap-server localhost:9092 --describe --topic test-topic

# Create topic
kafka-topics --bootstrap-server localhost:9092 --create --topic my-topic --partitions 3 --replication-factor 1

# Delete topic
kafka-topics --bootstrap-server localhost:9092 --delete --topic my-topic

# List consumer groups
kafka-consumer-groups --bootstrap-server localhost:9092 --list

# Describe consumer group
kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group test-group
```

---

## 🚀 Next Steps

1. **Read this guide** section by section
2. **Experiment** with our working system
3. **Use our APIs** to inspect Kafka internals
4. **Try scenarios** from section 10
5. **Build** your own Kafka application
6. **Monitor** with our monitoring APIs
7. **Optimize** based on your use case

**Remember:** Kafka is powerful but complex. Take your time, experiment, and use our 27 APIs to understand what's happening under the hood!

---

**Questions?** Use our APIs to explore:
- `GET /api/kafka/summary` - Complete cluster overview
- `GET /api/registry/summary` - Schema Registry status
- `GET /api/monitoring/consumer-groups/{groupId}/lag` - Consumer health

**Happy Learning!** 🎉

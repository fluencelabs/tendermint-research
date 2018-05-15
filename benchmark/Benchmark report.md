# Tendermint benchmark report
A series of tests was evaluated against several Tendermint clusters with various configurations.

## Conditions
* `Tendermint` with integrated `kvstore` application deployed on some EC2 nodes
* Tests were taken on 4-node, 16-node and 79-node clusters of t2.micro and t2.medium from one or several datacenter
* Every test is 20-second stream of random transactions broadcasted from one of cluster nodes
* Transactions are broadcasted via some fixed number of consequent Tendermint RPC calls every 100 milliseconds
* With perspective of `kvstore` application every transaction is random key-value pair added to key-value database

## Benchmark highlights
* The main limitation of throughput is transaction data rate. Peak throughput value is near 200 kB/s, reaching in 4-node local (single datacenter) cluster with large transaction sizes (4 kB and more)
* Throughput (in bytes) degrades when
  * Number of nodes grows (4-node cluster is 1.8-2 times faster than 16-node one)
  * Distance between nodes grows
  * Size of transaction goes down (4kB transactions are 1.5 times faster than 64B one)
* Throughput (in transactions) values for 128-byte transactions:
  * 1200 tx/s for 4-node single datacenter t2.micro cluster
  * 1125 tx/s for 4-node geo-distributed (4 regions) t2.micro cluster
  * 880 tx/s for 16-node geo-distributed (4 regions) t2.medium cluster
  * 640 tx/s for 16-node single datacenter (4 regions) t2.micro cluster
  * 90 tx/s for 79-node geo-distributed (4 regions) t2.medium cluster
* Latency values:
  * Are up to 2 second for 4-node and 16-node tests while transaction rate is below the maximal throughput
  * May degrade to 3-5 seconds when transaction rate reach the maximal throughput
  * May degrade to 10-15 seconds for 79-node test even for low transaction rate
* The value of maximum number of transaction per block is important and may depend on usage profile
  * Typically it should be near twice the average transaction rate
  * It should be large enough for high throughput-oriented workloads
  * It shouldn't be very large (more than 10000) if service may sometimes be overloaded – larger value may increase tx backlog processing after peak load
  * Reducing this value may help to optimize latency for low throughput workloads
  * The more powerful nodes' CPUs are the smaller this value should be

## Benchmark details
Several types of tests were taken in order to evaluate the dependency between maximal throughput / minimal latency and various factors:
* Cluster configuration
  * Number of nodes
  * Locations of nodes
  * Node hardware
* Transaction size
* Maximum transactions per block

### Cluster configuration tests
TODO

### Transaction size tests
TODO

### Maximum transactions per block tests
TODO

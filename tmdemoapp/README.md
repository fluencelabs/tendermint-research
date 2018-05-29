# Tendermint Demo ABCI KVStore on Scala
This is demo application implementing Tendermint ABCI interface. It models in-memory key-value string storage. Key here are hierarchical, `/`-separated. This key hierarchy is *merkleized*, so every node stores Merkle hash of its associated value (if present) and its children.
The application is compatible with `Tendermint v0.19.x` and uses `com.github.jtendermint.jabci` for Java ABCI definitions.

## Installation and running
For single-node run just launch the application:
```bash
sbt run
```
And launch Tendermint:
```bash
# uncomment line below to initialize Tendermint
#tendermint init

# uncomment line below to clear all Tendermint data
#tendermint unsafe_reset_all

tendermint node --consensus.create_empty_blocks=false
```

## Sending transactions
For working with transactions and queries use Python scripts in [`parse`](https://github.com/fluencelabs/tendermint_research/tree/master/parse) directory.
To set a new key-value mapping use:
```bash
python query.py localhost:46657 tx a/b=10
```
This would create hierarchical key `a/b` (if necessary) and map it to `10`

This script would output the height value corresponding to provided transaction. The height is available upon executing because `query.py` script uses `broadcast_tx_commit` RPC to send transactions to Tendermint. You can later find the latest transactions by running:
```bash
python parse_chain.py localhost:46657
```
This command would output last 50 non-empty blocks in chain with short summary about transactions. Here you can ensure that provided transaction indeed included in the block with height from response. This fact verifies that Tendermint majority (more than 2/3 of configured validator nodes) agreed on including this transaction in the mentioned block which certified by their signatures. Signature details (including information about all Consensus rounds and phases) can be found by requesting Tendermint RPC:
```bash
curl -s 'localhost:46657/block?height=_' # replace _ with actual height number
```
Also see `tmdemoapp` source code for special transaction cases:
* ranges (to insert multiple key-value pairs in single transaction)
* inserting comments (to deduplicate subsequent identical transaction)
* transactions where key and value are the same string
* wrong transaction stubs `BAD_CHECK` and `BAD_DELIVER` (to test Tendermint behavior on non-zero return code on ABCI `CheckTx` and `DeliverTx` methods)

In case of massive broadcasting of multiple transactions via `broadcast_tx_sync` or `broadcast_tx_async` RPC, the app would not calculate Merkle hashes during `DeliverTx` processing. Instead it would modify key tree and mark changed paths by clearing Merkle hashes until ABCI `Commit` processing. On `Commit` the app would recalculate Merkle hash along changed paths only. Finally the app would return the resulting root Merkle hash to Tendermint and this hash would be stored as `app_hash` for corresponding height in the blockchain.

Note that described merkleized structure is just for demo purposes and not self-balanced, it would remain efficient only until it the user transactions keep it relatively balanced. Something like [Patricia tree](https://github.com/ethereum/wiki/wiki/Patricia-Tree) should be more appropriate to achieve self-balancing.

## Making queries
Use `get:` queries to read values from KVStore:
```bash
python query.py localhost:46657 query get:a/b
```
Use `ls:` queries to read key hierarchy:
```bash
python query.py localhost:46657 query ls:a
```
These commands implemented by requesting `abci_query` RPC (which immediately proxies to ABCI `Query` in the app). Together with requested information the app method would return Merkle proof of this information. This Merkle proof is comma-separated list (`<level-1-proof>,<level-2-proof>,...`) of level proofs along the path to the requested key. For this implementation SHA-3 of a level in the list is exactly:
* either one of the space-separated item from the upper (the previous in comma-separated list) level proof;
* or the root app hash for the uppermost (the first) level proof.

The app stores historical changes and handle queries for any particular height. The requested height (the latest by default) and the corresponding `app_hash` also returned for `query` Python script. This combination (result, Merkle proof and `app_hash` from the blockchain) verifies the correctness of the result (because this `app_hash` could only appear in the blockchain as a result of Tendermint quorum consistent decision).
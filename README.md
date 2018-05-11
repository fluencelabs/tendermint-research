# Tendermint research
Research artifacts and tools for Tendermint

## Install guide for running Tendermint on EC2 cluster
This guide is intended for Fluence-specific Tendermint exploration. It is based on original documents for installing [Tendermint from the scratch](http://tendermint.readthedocs.io/en/master/install.html) and [deployment on Amazon EC2 via Ansible](http://tendermint.readthedocs.io/en/master/tools/ansible.html).

### Prerequisites
* AWS account with appropriate quotas to launch Linux instances in required regions (typically, t2.micro or t2.medium)
* Ansible installed on dev machine
* Python 2.7 installed on dev machine

### Preparation
Launch required number of instance on EC2. Perform those actions during Configuration step:
* Allow incoming TCP traffic to `46656` and `46657` ports
* Add `Environment` tag with `testnet-servers` value

Obtain deployment scripts from GitHub:
* Clone https://github.com/fluencelabs/tools repository
* Checkout `benchmark_improvements` branch
* Locate to `ansible` directory

### Quick start
To refresh node list, install, initialize, configure and start Tendermint cluster:
```bash
inventory/ec2.py --refresh-cache 1> /dev/null
ansible-playbook -i inventory/ec2.py network_install.yml -u ec2-user -b
ansible-playbook -i inventory/ec2.py network_prepare.yml -u ec2-user
ansible-playbook -i inventory/ec2.py network_start.yml -u ec2-user
```

### Other commands
Stopping cluster:
```bash
ansible-playbook -i inventory/ec2.py network_stop.yml -u ec2-user
```

Deleting cluster with all data:
```bash
ansible-playbook -i inventory/ec2.py network_delete.yml -u ec2-user
```

Checked cluster status: last committed block and transaction number:
```bash
ansible-playbook -i inventory/ec2.py network_status.yml -u ec2-user
```

Updating previously cluster nodes with Tendermint installed and prepared, after public IP change:
```bash
ansible-playbook -i inventory/ec2.py network_reassign.yml -u ec2-user
```

Changing configuration settings (with restart), e. g. for maximum number of transactions per block:
```bash
ansible-playbook -i inventory/ec2.py network_stop.yml -u ec2-user
ansible-playbook -i inventory/ec2.py network_configure.yml -e section=consensus -e option=max_block_size_txs -e value=5000 -u ec2-user
ansible-playbook -i inventory/ec2.py network_start.yml -u ec2-user
```

### Running benchmarks
Installing `tm-bench` tool (and also git and golang) on specific EC2 node (in ssh session):
```bash
sudo yum -y install git
wget https://dl.google.com/go/go1.10.1.linux-amd64.tar.gz
tar -xzf go1.10.1.linux-amd64.tar.gz
sudo mv go /usr/local
echo 'export GOROOT=/usr/local/go' >> ~/.bash_profile
echo 'export GOPATH=$HOME/Projects/Proj1' >> ~/.bash_profile
echo 'export PATH=$GOPATH/bin:$GOROOT/bin:$PATH' >> ~/.bash_profile
source ~/.bash_profile
go get -u github.com/fluencelabs/tools/tm-bench
cd $GOPATH/src/github.com/fluencelabs/tools/tm-bench
git checkout benchmark_improvements
make get_tools
make get_vendor_deps
make install
```

Launching benchmark from single node (in ssh session):
```bash
tm-bench -s 100 -r 300 -T 20 localhost:46657
```

The command above would run 20-second benchmark with 300 tx/s throughput and transaction size of 100 bytes.

Observe benchmark results using `parse/parse_chain.py` and `parse/parse_block.py` scripts in this project. The former prints quick overview of non-empty blocks' sizes in the chain, the latter prints various statistics and visualize the transaction backlog size for chosen blocks.
import sys, urllib, json, datetime, time

def uvarint(buf):
	x = long(0)
	s = 0
	for b in buf:
		if b < 0x80:
			return x | long(b) << s
		x |= long(b & 0x7f) << s
		s += 7
	return 0

def parseutc(utctxt):
	#tz conversion may be wrong
	now_timestamp = time.time()
	offset = datetime.datetime.fromtimestamp(now_timestamp) - datetime.datetime.utcfromtimestamp(now_timestamp)
	dt, _, tail = utctxt.partition(".")
	if tail == "":
		dt, _, _ = utctxt.partition("Z")
		tail = "0Z"
	pure = int((datetime.datetime.strptime(dt, '%Y-%m-%dT%H:%M:%S') + offset).strftime("%s"))
	ns = int(tail.rstrip("Z").ljust(9, "0"), 10)
	return pure + ns / 1e9
	
def formatbytes(value):
	if value < 1024:
		return "%.0f B" % value
	elif value < 1024 * 1024:
		return "%.3f KiB" % (value / 1024.0)
	else:
		return "%.3f MiB" % (value / 1024.0 / 1024.0)

def readjson(url):
	response = urllib.urlopen("http://" + url)
	return json.loads(response.read())

def getmaxheight():
	status = readjson(tmaddress + "/status")["result"]
	if "sync_info" in status: # compatibility
		return status["sync_info"]["latest_block_height"]
	else:
		return status["latest_block_height"]

if len(sys.argv) < 2:
	print "usage: python parse_block.py host:port minheight [maxheight]"
	sys.exit()

tmaddress = sys.argv[1]
minheight = int(sys.argv[2])
maxheight = int(sys.argv[3]) if len(sys.argv) > 3 else getmaxheight()

accsize = 0
acclatency = 0
minlatency = 1e20
maxlatency = 0
txcount = 0
blockcount = 0
firsttx = 1e20
lasttx = 0
firstblock = 1e20
lastblock = 0
maxblocksize = 0

txstat = []
for height in range(minheight, maxheight + 1):
	data = readjson(tmaddress + "/block?height=%d" % height)
	numtxs = data["result"]["block"]["header"]["num_txs"]
	
	blocktimetxt = data["result"]["block"]["header"]["time"]
	blocktime = parseutc(blocktimetxt)

	if numtxs > 0:
		firstblock = min(firstblock, blocktime)
		lastblock = max(lastblock, blocktime)
		blockcount += 1
		maxblocksize = max(maxblocksize, numtxs)

	print height, numtxs, blocktimetxt
	txs = data["result"]["block"]["data"]["txs"]
	if txs:
		for index, txhex in enumerate(txs):
			txbytes = bytearray.fromhex(txhex)
			key = chr(txbytes[0]) if chr(txbytes[1]) == '=' else "*" 
			connindex = uvarint(txbytes[2:8])
			txnumber = uvarint(txbytes[8:16])
			hostnamehash = txhex[32:64]
			
			txtime = uvarint(txbytes[32:40]) / 1e6
			if txtime < 1e9:
				txtime *= 1e6 # legacy support
			latency = blocktime - txtime

			accsize += len(txbytes)
			acclatency += latency
			minlatency = min(minlatency, latency)
			maxlatency = max(maxlatency, latency)
			txcount += 1
			firsttx = min(firsttx, txtime)
			lasttx = max(lasttx, txtime)

			txtimetxt = datetime.datetime.fromtimestamp(txtime)

			txstat.append((txtime, 1))
			txstat.append((blocktime, -1))
			if index < 5:
				print key, connindex, txnumber, hostnamehash, txtimetxt, latency

print "Transactions:    ", txcount, "=", formatbytes(accsize)
print "                 ", "%.3f s" % (lasttx - firsttx), "from", datetime.datetime.fromtimestamp(firsttx), "to", datetime.datetime.fromtimestamp(lasttx)
print "Blocks:          ", blockcount
print "                 ", "%.3f s" % (lastblock - firstblock), "from", datetime.datetime.fromtimestamp(firstblock), "to", datetime.datetime.fromtimestamp(lastblock)
print "Tx send rate:    ", "%.3f tx/s" % (txcount / (lasttx - firsttx)), "=", formatbytes(accsize / (lasttx - firsttx)) + "/s"
print "Tx throughput:   ", "%.3f tx/s" % (txcount / (lastblock - firsttx)), "=", formatbytes(accsize / (lastblock - firsttx)) + "/s"
print "Block throughput:", "%.3f block/s" % (blockcount / (lastblock - firsttx))
print "Avg tx latency:  ", "%.3f s" % (acclatency / txcount)
print "Min tx latency:  ", "%.3f s" % minlatency
print "Max tx latency:  ", "%.3f s" % maxlatency

txstat = sorted(txstat)
cursum = 0
curindex = 0
steps = 1000
stepstat = []
for i in range(steps + 1):
	t = firsttx + (lastblock - firsttx) / steps * i
	while curindex < len(txstat) and txstat[curindex][0] <= t:
		cursum += txstat[curindex][1] 
		curindex += 1
	stepstat.append(cursum)
import matplotlib.pyplot as plt
f = plt.figure(figsize=(15, 5))
plt.plot([i * (lastblock - firsttx) / steps for i in range(steps + 1)], stepstat)
plt.title("Duration: %.1f s, Tx size: %s, Tx send rate: %.3f tx/s = %s/s, Tx throughput: %.3f tx/s = %s/s" %
	(lasttx - firsttx, formatbytes(accsize / txcount), 
	txcount / (lasttx - firsttx), formatbytes(accsize / (lasttx - firsttx)),
	txcount / (lastblock - firsttx), formatbytes(accsize / (lastblock - firsttx))))
plt.xlabel("seconds from first tx")
plt.ylabel("txs in backlog")
f.savefig("tdmnt-stat-%d-%d-%d-%.1f-%.0f-%.0f.pdf" % 
	(minheight, maxheight, maxblocksize, lasttx - firsttx, accsize / txcount, txcount / (lasttx - firsttx)), bbox_inches='tight')
plt.show(block=True)
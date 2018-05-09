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
	print "usage: python parse_chain.py host:port [minheight]"
	sys.exit()

blocks_fetch = 20 # tendermint can't return more blocks
tmaddress = sys.argv[1]
maxheight = getmaxheight()
minheight = int(sys.argv[2]) if len(sys.argv) > 2 else max(1, maxheight - 49)

lastempty = -1
last_fetched_height = minheight - 1
for height in range(minheight, maxheight + 1):
	if height > last_fetched_height:
		last_fetched_height = min(height + blocks_fetch - 1, maxheight)
		bulk_data = (readjson(tmaddress + "/blockchain?minHeight=%d&maxHeight=%d" % (height, last_fetched_height)))["result"]["block_metas"]

	data = bulk_data[last_fetched_height - height]["header"]
	
	numtxs = data["num_txs"]
	totaltxs = data["total_txs"]

	blocktimetxt = data["time"]
	blocktime = parseutc(blocktimetxt)

	if numtxs > 0 or height == maxheight:
		print "%5s: %s %7d %7d" % (height, datetime.datetime.fromtimestamp(blocktime), numtxs, totaltxs)
	else:
		if lastempty < height - 1:
			print "..."
		lastempty = height
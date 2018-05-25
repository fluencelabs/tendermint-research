import sys, urllib, json, datetime, time

def readjson(url):
	response = urllib.urlopen("http://" + url)
	return json.loads(response.read())

def getsyncinfo():
	status = readjson(tmaddress + "/status")["result"]
	if "sync_info" in status: # compatibility
		return status["sync_info"]
	else:
		return status

if len(sys.argv) < 3:
	print "usage: python query.py host:port command arg"
	sys.exit()

tmaddress = sys.argv[1]
command = sys.argv[2]
arg = sys.argv[3]
if command == 'tx':
	response = readjson(tmaddress + '/broadcast_tx_commit?tx="' + arg + '"')
	if "error" in response:
		print "ERROR :", response["error"]["data"]
	else:
		height = response["result"]["height"]
		print "OK"
		print "HEIGHT:", height


elif command == 'query':
	syncinfo = getsyncinfo()
	height = syncinfo["latest_block_height"]
	apphash = syncinfo["latest_app_hash"]
	print "HEIGHT:", height
	print "HASH  :", apphash
	response = readjson(tmaddress + '/abci_query?height=' + str(height) + '&data="' + arg + '"')["result"]["response"]
	print "PROOF :", response["proof"].decode('base64') if "proof" in response else "NO_PROOF"
	print "RESULT:", response["value"].decode('base64') if "value" in response else "EMPTY"

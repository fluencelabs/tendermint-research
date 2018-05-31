package kvstore

import java.nio.ByteBuffer

import com.github.jtendermint.jabci.api._
import com.github.jtendermint.jabci.socket.TSocket
import com.github.jtendermint.jabci.types.{ResponseCheckTx, _}
import com.google.protobuf.ByteString

import scala.collection.mutable.ArrayBuffer

object KVStoreServerRunner extends IDeliverTx with ICheckTx with ICommit with IQuery {

  def main(args: Array[String]): Unit = {
    KVStoreServerRunner.start()
  }

  private val storage: ArrayBuffer[Node] = new ArrayBuffer[Node]()

  private var consensusRoot: Node = Node.emptyNode

  @volatile
  private var mempoolRoot: Node = Node.emptyNode

  def start(): Unit = {
    System.out.println("starting KVStore")
    val socket = new TSocket

    socket.registerListener(this)

    val t = new Thread(() => socket.start(46658))
    t.setName("KVStore server Main Thread")
    t.start()
    while (true) {
      Thread.sleep(1000L)
    }
  }

  override def receivedDeliverTx(req: RequestDeliverTx): ResponseDeliverTx = {
    val tx = req.getTx.toStringUtf8
    val txPayload = tx.split("###")(0)

    val unaryOpPattern = "(.+)=(.+):(.*)".r
    val binaryOpPattern = "(.+)=(.+):(.*),(.*)".r
    val keyValuePattern = "(.+)=(.*)".r
    val rangeKeyValuePattern = "(\\d+)-(\\d+):(.+)=(.*)".r

    txPayload match {
      case "BAD_DELIVER" =>
        System.out.println(s"DeliverTx: BAD_DELIVER")
        ResponseDeliverTx.newBuilder.setCode(CodeType.BAD).setLog("BAD_DELIVER").build
      case unaryOpPattern(key, op, arg) =>
        op match {
          case "get" =>
            val value = consensusRoot.getValue(arg).get
            addKeyValue(key, value)
            ResponseDeliverTx.newBuilder.setCode(CodeType.OK).setInfo(value).build
          case "increment" =>
            val value = consensusRoot.getValue(arg).get
            val newValue = (value.toLong + 1).toString
            addKeyValue(arg, newValue)
            addKeyValue(key, value)
            ResponseDeliverTx.newBuilder.setCode(CodeType.OK).setInfo(value).build
          case "factorial" =>
            val argValue = consensusRoot.getValue(arg).get.toLong
            val factorial = (1L to argValue).product.toString
            addKeyValue(key, factorial)
            ResponseDeliverTx.newBuilder.setCode(CodeType.OK).setInfo(factorial).build
          case _ =>
            ResponseDeliverTx.newBuilder.setCode(CodeType.BAD).setLog("Unknown unary op").build
        }
      case binaryOpPattern(key, op, arg1, arg2) =>
        op match {
          case "sum" =>
            val arg1Value = consensusRoot.getValue(arg1).get.toLong
            val arg2Value = consensusRoot.getValue(arg2).get.toLong
            val sum = (arg1Value + arg2Value).toString
            addKeyValue(key, sum)
            ResponseDeliverTx.newBuilder.setCode(CodeType.OK).setInfo(sum).build
          case _ =>
            ResponseDeliverTx.newBuilder.setCode(CodeType.BAD).setLog("Unknown binary op").build
        }
      case rangeKeyValuePattern(rangeStartStr, rangeEndStr, keyPattern, valuePattern) =>
        val rangeStart = rangeStartStr.toInt
        val rangeEnd = rangeEndStr.toInt
        System.out.println(s"DeliverTx: got range add from=$rangeStart to=$rangeEnd keyPattern=$keyPattern valuePattern=$valuePattern")
        for (index <- rangeStart until rangeEnd) {
          var key = keyPattern
          var value = valuePattern
          for (hexPosition <- 0 to 6) {
            val target = "@" + hexPosition
            val replacement = ((index >> hexPosition * 4) & 0xf).toHexString
            key = key.replace(target, replacement)
            value = value.replace(target, replacement)
          }
          addKeyValue(key, value)
        }
        ResponseDeliverTx.newBuilder.setCode(CodeType.OK).build
      case keyValuePattern(key, value) =>
        addKeyValue(key, value)
        ResponseDeliverTx.newBuilder.setCode(CodeType.OK).setInfo(value).build
      case key =>
        addKeyValue(key, key)
        ResponseDeliverTx.newBuilder.setCode(CodeType.OK).setInfo(key).build
    }
  }

  private def addKeyValue(key: String, value: String): Unit = {
    consensusRoot = consensusRoot.addValue(key, value)
    System.out.println(s"DeliverTx: added key=$key value=$value")
  }

  override def requestCheckTx(req: RequestCheckTx): ResponseCheckTx = {
    // check mempoolRoot

    val tx = req.getTx.toStringUtf8
    if (tx == "BAD_CHECK") {
      System.out.println(s"CheckTx: $tx BAD")
      ResponseCheckTx.newBuilder.setCode(CodeType.BAD).setLog("BAD_CHECK").build
    } else {
      System.out.println(s"CheckTx: $tx OK")
      ResponseCheckTx.newBuilder.setCode(CodeType.OK).build
    }
  }

  override def requestCommit(requestCommit: RequestCommit): ResponseCommit = {
    consensusRoot = consensusRoot.merkleize()

    val buf = ByteBuffer.allocate(32)
    buf.put(consensusRoot.merkleHash.get)
    buf.rewind

    storage.append(consensusRoot)
    mempoolRoot = consensusRoot

    ResponseCommit.newBuilder.setData(ByteString.copyFrom(buf)).build
  }

  override def requestQuery(req: RequestQuery): ResponseQuery = {
    val height = if (req.getHeight != 0) req.getHeight.toInt - 1 else storage.size - 1
    val root = storage(height)
    val getPattern = "get:(.*)".r
    val lsPattern = "ls:(.*)".r

    val query = req.getData.toStringUtf8
    query match {
      case getPattern(key) =>
        val result = root.getValue(key)
        val proof = if (result.isDefined && req.getProve) twoLevelMerkleListToString(root.getProof(key)) else ""

        ResponseQuery.newBuilder.setCode(CodeType.OK)
          .setValue(ByteString.copyFromUtf8(result.getOrElse("")))
          .setProof(ByteString.copyFromUtf8(proof))
          .build
      case lsPattern(key) =>
        val result = root.listChildren(key)
        val proof = if (result.isDefined && req.getProve) twoLevelMerkleListToString(root.getProof(key)) else ""

        ResponseQuery.newBuilder.setCode(CodeType.OK)
          .setValue(ByteString.copyFromUtf8(result.map(x => x.mkString(" ")).getOrElse("")))
          .setProof(ByteString.copyFromUtf8(proof))
          .build
      case _ =>
        ResponseQuery.newBuilder.setCode(CodeType.BAD).setLog("Invalid query path. Got " + query).build
    }
  }

  private def twoLevelMerkleListToString(list: List[List[MerkleHash]]): String =
    list.map(level => level.map(MerkleUtil.merkleHashToHex).mkString(" ")).mkString(", ")
}

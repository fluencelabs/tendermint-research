package kvstore

import java.nio.ByteBuffer
import java.util

import com.github.jtendermint.jabci.api._
import com.github.jtendermint.jabci.socket.TSocket
import com.github.jtendermint.jabci.types.{ResponseCheckTx, _}
import com.google.protobuf.ByteString

object KVStoreServerRunner extends IDeliverTx with ICheckTx with ICommit with IQuery {

  def main(args: Array[String]): Unit = {
    KVStoreServerRunner.start()
  }

  private val storage: util.ArrayList[Node] = new util.ArrayList[Node]()
  private var stageRoot: Node = Node.emptyNode

  def start(): Unit = {
    System.out.println("starting KVStore")
    val socket = new TSocket

    socket.registerListener(this)

    val t = new Thread(() => socket.start(46658))
    t.setName("KVStore server Main Thread")
    t.start()
    while (true)
      Thread.sleep(1000L)
  }

  override def receivedDeliverTx(req: RequestDeliverTx): ResponseDeliverTx = {
    val keyValuePattern = "(.*)=(.*)".r
    val rangeKeyValuePattern = "(.*)=(.*)".r

    val tx = req.getTx.toStringUtf8
    tx match {
      case keyValuePattern(key, value) =>
        System.out.println("got key=" + key + " value=" + value)
        stageRoot = stageRoot.addValue(key, value)
        ResponseDeliverTx.newBuilder.setCode(CodeType.OK).build
      case key =>
        System.out.println("got key/value=" + key)
        stageRoot = stageRoot.addValue(key, key)
        ResponseDeliverTx.newBuilder.setCode(CodeType.OK).build
    }
  }

  override def requestCheckTx(req: RequestCheckTx): ResponseCheckTx = {
    System.out.println("SENDING OK")
    ResponseCheckTx.newBuilder.setCode(CodeType.OK).build
  }

  override def requestCommit(requestCommit: RequestCommit): ResponseCommit = {
    stageRoot = stageRoot.merkleize()

    val buf = ByteBuffer.allocate(32)
    buf.put(stageRoot.merkleHash.get)
    buf.rewind

    storage.add(stageRoot)
    ResponseCommit.newBuilder.setData(ByteString.copyFrom(buf)).build
  }

  override def requestQuery(req: RequestQuery): ResponseQuery = {
    val height = if (req.getHeight != 0) req.getHeight.toInt - 1 else storage.size() - 1
    val root = storage.get(height)
    val getPattern = "get:(.*)".r
    val lsPattern = "ls:(.*)".r

    val query = req.getData.toStringUtf8
    query match {
      case getPattern(key) =>
        val result = root.getValue(key)
        val proof = if (!req.getProve) "" else twoLevelMerkleListToString(root.getProof(key))

        ResponseQuery.newBuilder.setCode(CodeType.OK)
          .setValue(ByteString.copyFromUtf8(result.getOrElse("")))
          .setProof(ByteString.copyFromUtf8(proof))
          .build
      case lsPattern(key) =>
        val result = root.listChildren(key)
        val proof = if (!req.getProve) "" else twoLevelMerkleListToString(root.getProof(key))

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

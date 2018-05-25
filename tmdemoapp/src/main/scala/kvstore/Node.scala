package kvstore

import kvstore.MerkleUtil._
import scala.collection.immutable.HashMap

case class Node(children: NodeStorage, value: Option[String], merkleHash: Option[MerkleHash]) {
  def merkleize(): Node =
    if (merkleHash.isDefined)
      this
    else {
      val newChildren = children.mapValues(x => x.merkleize())
      val withNewChildren = Node(newChildren, value, None)
      Node(newChildren, value, Some(mergeMerkle(withNewChildren.merkleItems(), HEX_BASED_MERKLE_MERGE)))
    }

  private def merkleItems(): List[MerkleHash] =
    singleMerkle(value.getOrElse("")) :: children.flatMap(x => List(singleMerkle(x._1), x._2.merkleHash.get)).toList

  def getProof(key: String): List[List[MerkleHash]] = {
    if (key.isEmpty)
      List(merkleItems())
    else {
      val (next, rest) = splitPath(key)
      merkleItems() :: children(next).getProof(rest)
    }
  }

  def addValue(key: String, value: String): Node = {
    if (key.isEmpty)
      Node(children, Some(value), None)
    else {
      val (next, rest) = splitPath(key)
      Node(children + (next -> children.getOrElse(next, Node.emptyNode).addValue(rest, value)), this.value, None)
    }
  }

  def getValue(key: String): Option[String] = {
    if (key.isEmpty)
      value
    else {
      val (next, rest) = splitPath(key)
      children.get(next).flatMap(_.getValue(rest))
    }
  }

  def listChildren(key: String): Option[List[String]] = {
    if (key.isEmpty)
      Some(children.keys.toList)
    else {
      val (next, rest) = splitPath(key)
      children.get(next).flatMap(_.listChildren(rest))
    }
  }

  private def splitPath(path: String): (String, String) = {
    val (next, rest) = path.span(_ != '/')
    (next, rest.replaceFirst("/", ""))
  }
}

object Node {
  val emptyNode: Node = Node(HashMap.empty[String, Node], None, None)
}
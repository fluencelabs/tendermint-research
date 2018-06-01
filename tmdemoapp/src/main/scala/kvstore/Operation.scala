package kvstore

trait Operation {
  def run(root: Node, targetKey: String): Either[String, (Node, String)]
}

case class SetValueOperation(value: String) extends Operation {
  override def run(root: Node, targetKey: String): Either[String, (Node, String)] = {
    System.out.println(s"process set value=$value")
    Right(root.add(targetKey, value), value)
  }
}

case class GetOperation(arg: String) extends Operation {
  override def run(root: Node, targetKey: String): Either[String, (Node, String)] = {
    System.out.println(s"process get arg=$arg")
    val value = root.getValue(arg)
    value.toRight("Wrong key").map(x => (root.add(targetKey, x), x))
  }
}

case class IncrementOperation(arg: String) extends Operation {
  override def run(root: Node, targetKey: String): Either[String, (Node, String)] = {
    System.out.println(s"process increment arg=$arg")
    val oldValue = root.getValue(arg).get
    val newValue = (oldValue.toLong + 1).toString
    Right(root.add(targetKey, oldValue).add(arg, newValue), oldValue)
  }
}

case class FactorialOperation(arg: String) extends Operation {
  override def run(root: Node, targetKey: String): Either[String, (Node, String)] = {
    System.out.println(s"process factorial arg=$arg")
    val argValue = root.getValue(arg).get.toLong
    val factorial = (1L to argValue).product.toString
    Right(root.add(targetKey, factorial), factorial)
  }
}

case class SumOperation(arg1: String, arg2: String) extends Operation {
  override def run(root: Node, targetKey: String): Either[String, (Node, String)] = {
    System.out.println(s"process sum arg1=$arg1 arg2=$arg2")
    val arg1Value = root.getValue(arg1).get.toLong
    val arg2Value = root.getValue(arg2).get.toLong
    val sum = (arg1Value + arg2Value).toString
    Right(root.add(targetKey, sum), sum)
  }
}


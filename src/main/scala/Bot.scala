class ControlFunctionFactory {
def create = new ControlFunction().respond _
}

class ControlFunction {
  val a = 2
  def respond(input: String) = {
    CommandParser(input) match {
      case React(generation: Int, tie: Int, view: String, energy: String, name: String, master: Seq[(Int, Int)]) => {
        val r = Math.random
        Move(direction = Heading(1, 2)).toString
    }
      case _ => ""
  }
}
}
class ControlFunctionFactory {
def create = new ControlFunction().respond _
}
class ControlFunction {
  val a = 2
  def respond(input: String) = "Status(text=Hello Scalatrondsfdsfdsf!)"
}
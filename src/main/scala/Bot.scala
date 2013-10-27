class ControlFunctionFactory {
def create = new ControlFunction().respond _
}
class ControlFunction {
  def respond(input: String) = "Status(text=Hello Scalatron!)"
}
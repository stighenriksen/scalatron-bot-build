package scalatron.botwar.botPlugin.sti
class ControlFunctionFactory {
def create = new ControlFunction().respond _
}
class ControlFunction {
  def respond(input: String) = "Status(text=Hello Worldscck" + mybot.Utils.stupid() + ")"
}
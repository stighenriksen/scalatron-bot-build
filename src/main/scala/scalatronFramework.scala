import scala.util.{Random, Try}

// -------------------------------------------------------------------------------------------------
// Framework (https://gist.github.com/jedws/2643617#file-scalatronframework-scala)
// -------------------------------------------------------------------------------------------------

/** Simple typeclass for turning Strings into things */
trait Mapper[A] extends (String => A)
/** define all the Mapper typeclass instances and generators we need */
object Mapper {
  implicit val StringMapper = new Mapper[String] {
    def apply(s: String) = s
  }
  implicit val IntMapper = new Mapper[Int] {
    def apply(s: String) = s.toInt
  }
  implicit def SeqMapper[A: Mapper] = new Mapper[Seq[A]] {
    def apply(s: String) = s.split("|").map(implicitly[Mapper[A]])
  }
  implicit def Tuple2Mapper[A: Mapper, B: Mapper] = new Mapper[(A, B)] {
    def apply(s: String) = {
      val a = s.split("|")
      implicitly[Mapper[A]].apply(a(0)) -> implicitly[Mapper[B]].apply(a(1))
    }
  }
}

trait Parameters {
  def apply[A: Mapper](s: String): A
  def get(s: String): Option[String]
}

object Parameters {
  implicit def StringyMapToParameters(m: Map[String, String]) = new Parameters {
    def apply[A: Mapper](s: String): A = implicitly[Mapper[A]].apply(m(s))
    def get(s: String) = m.get(s)
  }
}

// -------------------------------------------------------------------------------------------------
/**
 * Utility methods for parsing strings containing a single command of the format
 * "Command(key=value,key=value,...)"
 */
object CommandParser {
  /** "Command(..)" => ("Command", Map( ("key" -> "value"), ("key" -> "value"), ..}) */
  def apply(command: String): Command = {
    /** "key=value" => ("key","value") */
    def split(param: String): (String, String) = {
      val seg = param.split('=')
      (seg(0), if (seg.length >= 2) seg(1) else "")
    }

    val segments = command.split('(')
    require(segments.length == 2, "invalid command: " + command)
    Command(segments(0), segments(1).dropRight(1).split(',').map(split).toMap)
  }
}

object Command {
  def apply(cmd: String, get: Parameters): Command = cmd match {
    case "Welcome" => Welcome(get[String]("name"), get[Int]("apocalypse"), get[Int]("round"))
    case "React"   => React(get[Int]("generation"), get[Int]("time"), get[String]("view"), get[String]("energy"), get[String]("name"), get)
    case "Goodbye" => Goodbye(get[Int]("energy"))
  }
}

sealed trait Command
/**
 * Welcome(name=String,path=string,apocalypse=int,round=int)
 * “Welcome” is the first command sent by the server to a plug-in before any other invocations of the control function.
 *
 * Parameters:
 *
 * name: the player name associated with the plug-in. The player name is set based on the name of the directory containing the plug-in.
 * path: the path of the directory from which the server loaded the plug-in (which the plug-in would otherwise have no way of knowing about, aside from a hard-coded string). Contains no terminating slash. The plug-in can store this path and create log-files in it, ideally incorporating the round index provided in the round parameter and optionally the time (step index) and entity name passed with each later React command. Note: copious logging will slow down gameplay, so be reasonable and restrict logging to specific rounds and steps. For suggestions, refer to the Debugging section in the Scalatron Tutorial.
 * apocalypse: the number of steps that will be performed in the upcoming game round. This allows bots to plan ahead and to e.g. schedule the recall of harvesting drones. Keep in mind, however, that the control function of master bots is only invoked with React every second simulation step! See the Game Rules for details.
 * round: the index of the round for which the control function was instantiated. A game server continually runs rounds of the game, and the round index is incremented each time.
 */
case class Welcome(name: String, apocalypse: Int, round: Int) extends Command

/**
 * React(generation=int,name=string,time=int,view=string,energy=string,master=int:int,…)
 * “React” is invoked by the server once for each entity for each step in which the entity is allowed to move (mini-bots every cycle, bots every second cycle - see the Game Rules for details). The plug-in must return a response for the entity with the given entity name that is appropriate for the given view of the world.
 *
 * Parameters:
 *
 * generation: the generation of this bot. The master bot is the only bot of generation 0 (zero);? the mini-bots it spawned are of generation 1 (one); the mini-bots spawned by ? these are generation 2 (two), etc. Use this parameter to distinguish between ? mini-bots (slaves) and your master bot.
 * name: the name of the entity. For master bots, this is the name of the player (which in turn is the name of the plug-in directory the bot was loaded from). For mini-bots, this is either the name provided to Spawn() or a default name that was auto-generated by the server when the mini-bot was spawned.
 * time: a non-negative, monotonically increasing integer that represents the simulation time (basically a simulation step counter).
 * view: the view that the player has of the playing field. The view is a square region containing N*N cells, where N is the width and height of the region. Each cell is represented as a single ASCII character. The meaning of the characters is defined in the table View/Cell Encoding.
 * energy: the entity's current energy level
 * master: for mini-bots only: relative position of the master, in cells, in the format “x:y”, e.g. “-1:1”. To return to the master, the mini-bot can use this as the move direction (with some obstacle avoidance, of course).
 * In addition to these system-generated parameters, the server passes in all state parameters of the entity that were set by the player via Spawn() or Set() (see below). If, for example, a mini-bot was spawned with Spawn(...,role=missile), the React invocation will contain a parameter called role with the value missile.
 *
 * The control function is expected to return a valid response, which may consist of zero or more commands separated by a pipe (|) character. The available commands are listed in the section Opcodes of Plugin-to-Server Commands.
 */
case class React(generation: Int, time: Int, view: String, energy: String, name: String, get: Parameters) extends Command

/**
 * “Goodbye” is the last command sent by the server to a plug-in after all other invocations. The plug-in should use this opportunity to close any open files (such as those used for debug logging) and to relinquish control of any other resources it may hold.
 *
 * energy: the bot's final energy level
 */
case class Goodbye(energy: Int) extends Command

sealed trait Property
case class DirectionProperty() extends Property
case class Generation() extends Property
case class Name() extends Property
case class Energy() extends Property
case class Time() extends Property
case class ViewProperty() extends Property
case class Master() extends Property

sealed trait MiniOp {
  def and(that: Seq[Option[MiniOp]]) = this.toString + that.flatten.mkString("|","|","")
}
sealed trait Op extends MiniOp

/**
 * Move(direction=int:int)
 * Moves the bot one cell in a given direction, if possible. The delta values are signed integers. The permitted values are -1, 0 or 1.
 *
 * Parameters:
 * direction desired displacement for the move, e.g. 1:1 or 0:-1
 *
 * Example:
 * Move(direction=-1:1) moves the entity left and down.
 *
 * Energy Cost/Permissions:
 * for master bot: 0 EU (free)
 * for mini-bot: 0 EU (free)
 */
case class Move(direction: Heading) extends Op {
  override def toString = "Move(direction=%s)".format(direction)
}

/**
 * Spawn(direction=int:int,name=string,energy=int,…)
 * Spawns a mini-bot from the position of the current entity at the given cell position, expressed relative to the current position.
 *
 * Parameters:
 *
 * direction: desired displacement for the spawned mini-bot, e.g. -1:1
 * name: arbitrary string, except the following characters are not permitted: |, ,, =, (
 * energy: energy budget to transfer to the spawned mini-bot (minimum: 100 EU)
 *
 * Defaults:
 * name = Slave_nn an auto-generated unique slave name
 * energy = 100 the minimum permissible energy
 *
 * Additional Parameters:
 * In addition to the parameters listed above, the command can contain arbitrary additional parameter key/value pairs. These will be set as the initial state parameters of the entity and will be passed along to all subsequent control function invocations with React. This allows a master bot to “program” a mini-bot with arbitrary starting parameters.
 * The usual restrictions for strings apply (no comma, parentheses, equals sign or pipe characters).
 * The following property names are reserved and must not be used for custom properties: generation, name, energy, time, view, direction, master.
 * Properties whose values are empty strings are ignored.
 * Example:
 *
 * Spawn(direction=-1:1,energy=100) spawns a new mini-bot one cell to the left and one cell down, with an initial energy of 100 EU.
 *
 * Energy Cost/Permissions:
 * for master bot: as allocated via energy
 * for mini-bot: as allocated via energy
 *
 * Note that this means that mini-bots can spawn other mini-bots (if they have the required energy, i.e. at least 100 EU).
 */
case class Spawn(direction: Heading, name: Option[String] = None, energy: Int = 100) extends Op {
  require(energy >= 100, "energy must be >= than 100")
  override def toString =
    Util.string("Spawn", "direction" -> direction, "energy" -> energy, "name" -> name)
}

/**
 * Set(key=value,…)
 * Sets one or more state parameters with the given names to the given values. The state parameters of the entity will be passed along to all subsequent control function invocations with React. This allows an entity to store state information on the server, making its implementation immutable and delegating state maintenance to the server.
 *
 * The usual restrictions for strings apply (no comma, parentheses, equals sign or pipe characters).
 *
 * The following property names are reserved and must not be used for custom properties: generation, name, energy, time, view, direction, master.
 * Properties whose values are empty strings are deleted from the state properties.
 *
 * No Energy Cost/ All bots are permitted.
 */
case class Set(map: Map[String, String]) extends Op {
  override def toString = Util.string("Set", map.toSeq: _*)
}

//
// simulation neutral
//

/**
 * Say(text=string)
 * Displays a little text bubble that remains at the position where it was created. Use this to drop textual breadcrumbs associated with events. You can also use this as a debugging tool. Don't go overboard with this, it'll eventually slow down the gameplay.
 *
 * Parameters:
 * text the message to display; maximum length: 10 chars; can be an arbitrary string, except the following characters are not permitted: |, ,, =, (
 *
 * Energy Cost/Permissions:
 * for master bot: permitted, no energy consumed
 * for mini-bot: permitted, no energy consumed
 */
case class Say(text: String) extends Op {
  override def toString = Util.string("Say", "text" -> text)
}

/**
 * Status(text=string)
 * Shortcut for setting the state property 'status', which displays a little text bubble near the entity which moves around with the entity. Use this to tell spectators about what your bot thinks. You can also use this as a debugging tool. If you return the opcode Status, do not also set the status property via Set, since no particular order of execution is guaranteed.
 *
 * Parameters:
 * text the message to display; maximum length: 20 chars; can be an arbitrary string, except the following characters are not permitted: |, ,, =, (
 *
 * Energy Cost/Permissions:
 * for master bot: permitted, no energy consumed
 * for mini-bot: permitted, no energy consumed
 */
case class Status(text: String) extends Op {
  override def toString = Util.string("Status", "text" -> text)
}

/**
 * MarkCell(position=int:int,color=string)
 * Displays a cell as marked. You can use this as a debugging tool.
 *
 * Parameters:
 * position desired displacement relative to the current bot, e.g. -2:4 (defaults to 0:0)
 * color color to use for marking the cell, using HTML color notation, e.g. #ff8800 (default: #8888ff)
 *
 * Energy Cost/Permissions:
 * for master bot: permitted, no energy consumed
 * for mini-bot: permitted, no energy consumed
 */
case class MarkCell(position: Heading, color: Color) extends Op {
  override def toString = Util.string("MarkCell", "position" -> position, "color" -> color)
}

/**
 * DrawLine(from=int:int,to=int:int,color=string)
 * Draws a line. You can use this as a debugging tool.
 *
 * Parameters:
 * from starting cell of the line to draw, e.g. -2:4 (defaults to 0:0)
 * to destination cell of the line to draw, e.g. 3:-2 (defaults to 0:0)
 * color color to use for marking the cell, using HTML color notation, e.g. #ff8800 (default: #8888ff)
 *
 * Energy Cost/Permissions:
 * for master bot: permitted, no energy consumed
 * for mini-bot: permitted, no energy consumed
 */
case class DrawLine(from: Heading, to: Heading, color: Color) extends Op {
  override def toString = Util.string("DrawLine", "from" -> from, "to" -> to, "color" -> color)
}

/**
 * Log(text=string)
 * Shortcut for setting the state property debug, which by convention contains an optional (multi-line) string with debug information related to the entity that issues this opcode. This text string can be displayed in the browser-based debug window to track what a bot or mini-bot is doing. The debug information is erased each time before the control function is called, so there is no need to set it to an empty string.
 *
 * Parameters:
 * text the debug message to store. The usual restrictions for string values apply (no commas, parentheses, equals signs or pipe characters). Newline characters are permitted, however.
 *
 * Energy Cost/Permissions:
 * for master bot: permitted, no energy consumed
 * for mini-bot: permitted, no energy consumed
 */
case class Log(text: String) extends Op {
  override def toString = Util.string("Log", "text" -> text)
}

/**
 * Explode(size=int)
 * Detonates the mini-bot, dissipating its energy over some blast radius and damaging nearby entities. The mini-bot disappears. Parameters:
 *
 * size an integer value 2 < x < 10 indicating the desired blast radius
 *
 * Energy Cost/Permissions:
 * for master bot: cannot explode itself
 * for mini-bot: entire stored energy
 */
case class Explode(size: Int) extends MiniOp {
  require(2 < size && size < 10)
  override def toString = Util.string("Explode", "size" -> size)
}

object Color {
  private def req(i: Int): Boolean = {require(i < 256 && i >= 0); true}

  //TODO define color constants
}

case class Color(r: Int, g: Int, b: Int) {
  Color.req(r) && Color.req(g) && Color.req(b)
  override def toString = "#" + r.toHexString + g.toHexString + b.toHexString
}

sealed trait Cell
object Cell {
  case object Unknown extends Cell
  case object Empty extends Cell
  case object Wall extends Cell
  case object You extends Cell
  case object Enemy extends Cell
  case object YourSlave extends Cell
  case object EnemySlave extends Cell
  case object GoodPlant extends Cell // 
  case object BadPlant extends Cell
  case object GoodBeast extends Cell
  case object BadBeast extends Cell

  def apply(c: Char): Cell = c match {
    case '?' => Unknown
    case '_' => Empty
    case 'W' => Wall
    case 'M' => You
    case 'm' => Enemy
    case 'S' => YourSlave
    case 's' => EnemySlave
    case 'P' => GoodPlant
    case 'p' => BadPlant
    case 'B' => GoodBeast
    case 'b' => BadBeast
  }
}

sealed trait Displacement { def value: Int }
object Displacement {
  case object Neg extends Displacement { val value = -1 }
  case object Zero extends Displacement { val value = 0 }
  case object Pos extends Displacement { val value = 1 }

  def apply(in: Int) = in match {
    case -1 => Neg
    case 0  => Zero
    case 1  => Pos
  }
}

object Util {
  def parse[A](s: String)(f: (Int, Int) => A): A = {
    val a = s.split(':')
    f(a(0).toInt, a(1).toInt)
  }

  def string(name: String, is: (String, Any)*): String = {
    val w = new java.io.StringWriter().append(name).append("(")
    is.foreach {
      case (n, None)    =>
      case (n, Some(v)) => w.append(n).append("=").append(v.toString)
      case (n, v)       => w.append(n).append("=").append(v.toString)
    }
    w.append(")").toString
  }
}

object Heading {
  /** parse a value from Heading.toString format, e.g. "0:1". */
  def random(rnd: Random): Heading = Directions(rnd.nextInt(8))

  def parse(s: String): Heading = Util.parse(s)(Heading.apply)
  def apply(x: Int, y: Int): Heading =
    (x, y) match {
      case (1,1) => NorthEast
      case (1,0) => East
      case (1,-1) => SouthEast
      case (0,1) => North
      case (0,0) => Nowhere
      case (0,-1) => South
      case (-1,1) => NorthWest
      case (-1,0) => West
      case (-1,-1) => SouthWest
    }

  import Displacement.{ Pos, Zero, Neg }
  val East = new Heading(Pos, Zero)
  val NorthEast = Heading(Pos, Pos)
  val North = Heading(Zero, Pos)
  val NorthWest = Heading(Neg, Pos)
  val West = Heading(Neg, Zero)
  val SouthWest = Heading(Neg, Neg)
  val South = Heading(Zero, Neg)
  val SouthEast = Heading(Pos, Neg)
  val Directions = Array(East, NorthEast, North, NorthWest, West, SouthWest, South, SouthEast)

  val Nowhere = Heading(0, 0)
}

case class Heading private (x: Displacement, y: Displacement) {
  override def toString = x.value + ":" + y.value
}

object Coord {
  implicit def HeadingToCoord(m: Heading) = Coord(m.x.value, m.y.value)

  /** parse a value from Coord.toString format, e.g. "0:1". */
  def parse(s: String): Coord = Util.parse(s)(Coord.apply)
}

case class Coord(x: Int, y: Int) {
  override def toString = x + ":" + y

  def isNonZero = x != 0 || y != 0
  def isZero = x == 0 && y == 0
  def isNonNegative = x >= 0 && y >= 0

  def updateX(newX: Int) = Coord(newX, y)
  def updateY(newY: Int) = Coord(x, newY)

  def addToX(dx: Int) = Coord(x + dx, y)
  def addToY(dy: Int) = Coord(x, y + dy)

  def +(pos: Coord) = Coord(x + pos.x, y + pos.y)
  def -(pos: Coord) = Coord(x - pos.x, y - pos.y)
  def *(factor: Double) = Coord((x * factor).intValue, (y * factor).intValue)

  def distanceTo(pos: Coord): Double = (this - pos).length // Phythagorean
  def length: Double = math.sqrt(x * x + y * y) // Phythagorean

  def stepsTo(pos: Coord): Int = (this - pos).stepCount // steps to reach pos: max delta X or Y
  def stepCount: Int = x.abs.max(y.abs) // steps from (0,0) to get here: max X or Y

  def signum = Coord(x.signum, y.signum)
  def heading = Heading(x.signum, y.signum)

  def wrap(size: Coord) = {
    def fix(a: Int, len: Int) = if (a < 0) len + a else if (a >= len) a % len else a
    val (xx, yy) = (fix(x, size.x), fix(y, size.y))
    if (xx != x || yy != y) Coord(xx, yy) else this
  }
}

object View {
  trait Projection extends (Coord => Cell) {
    def indexFrom(c: Coord): Int
    def fromAbsolute(c: Coord): Coord
    def fromRelative(c: Coord): Coord
    def fromIndex(index: Int): Coord
  }
}

case class View(cells: String) extends (Coord => Cell) {
  val size = math.sqrt(cells.length).toInt
  val center = Coord(size / 2, size / 2)

  def apply(c: Coord): Cell = Relative(c)

  def offsetToNearest(c: Char): Option[Coord] =
      cells.view.zipWithIndex.filter(_._1 == c).map(p => Relative.fromIndex(p._2)).sortBy(_.length).headOption

  object Relative extends View.Projection {
    def indexFrom(c: Coord) = Absolute.indexFrom(Absolute.fromRelative(c))
    def fromAbsolute(c: Coord) = c - center
    def fromIndex(index: Int) = fromAbsolute(Absolute.fromIndex(index))
    def fromRelative(c: Coord) = c
    def apply(c: Coord) = Cell(cells.charAt(indexFrom(c)))
  }

  object Absolute extends View.Projection {
    def indexFrom(c: Coord) = c.x + c.y * size
    def fromIndex(index: Int) = Coord(index % size, index / size)
    def fromRelative(c: Coord) = c + center
    def fromAbsolute(c: Coord) = c
    def apply(c: Coord) = Cell(cells.charAt(indexFrom(c)))
  }
}
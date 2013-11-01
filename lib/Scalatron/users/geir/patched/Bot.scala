package scalatron.botwar.botPlugin.geir
// Scalatron expects a class named ControlFunctionFactory which returns a
// function (String) => String


object ControlFunction {
  val RotationDelay = 16 // wait this many simulation steps before turning

  def forMaster(bot: Bot) {
    val heading = bot.inputAsXYOrElse("heading", XY(0, 1))
    bot.move(heading)

    val nearestPlant: Option[XY] = bot.view.offsetToNearest(Cell.GoodPlant)
    val nearestEnemy: Option[XY] = bot.view.offsetToNearest(Cell.Enemy)
    val nearestWall: Option[XY] = bot.view.offsetToNearest(Cell.Wall)
    val me: Option[XY] = bot.view.offsetToNearest(Cell.You)

    val dontFireAggressiveMissileUntil = bot.inputAsIntOrElse("dontFireAggressiveMissileUntil", -1)
    val dontFireDefensiveMissileUntil = bot.inputAsIntOrElse("dontFireDefensiveMissileUntil", -1)



    import java.util.Random
    val rnd = new Random
    val run = rnd.nextBoolean

    if(run){
      bot.move(XY(rnd.nextInt, rnd.nextInt)).say("running!").toString
      if(nearestEnemy.isDefined){
        bot.spawn(nearestEnemy.get.signum, "mood" -> "Aggressive")
      }


    }else {
      (nearestPlant, nearestWall) match {
        case (Some(plant),Some(wall)) => if (me.get.distanceTo(wall) < 2)
          bot.move(wall.signum.negate.rotateClockwise90).say("Move from wall!!").toString else bot.move(plant.signum).say("Eat!!").toString
        case _ => bot.move(XY(rnd.nextInt, rnd.nextInt)).say("Standing").toString
      }
    }


    /*
    val view = bot.view
    view.offsetToNearest(Cell.GoodPlant) match {
      case Some(offset) =>
        val unitOffset = offset.signum
        bot.move(unitOffset).say("Eat!!").toString
      case None => view.offsetToNearest(Cell.Wall) match {
        case Some(offset) =>
          val unitOffset = offset.signum.negate

          bot.move(unitOffset).say("Move from wall!!").toString
        case None => ""

      }
    }  */
  }

  def forSlave(bot: MiniBot) {
    val actualOffset = bot.offsetToMaster.negate // as seen from master
    val desiredOffset = bot.inputAsXYOrElse("offset", XY(10, 10)) // as seen from master
    bot.move((desiredOffset - actualOffset).signum)
  }
}

class ControlFunctionFactory {
  // Returning the 'HelloBot'. Try to alter the 'HelloBot', or create a new bot
  // and return its respond function here!
  def create = new GeirBot().respond _
}

/*
      For documentation of opcodes and parameters see:
      https://github.com/scalatron/scalatron/blob/master/Scalatron/doc/markdown/Scalatron%20Protocol.md

      The rules, including some strategies:
      https://github.com/scalatron/scalatron/blob/master/Scalatron/doc/markdown/Scalatron%20Game%20Rules.md

      Tutorial, with a lot of Scala help:
      https://github.com/scalatron/scalatron/blob/master/Scalatron/doc/markdown/Scalatron%20Tutorial.md

      Even more documentation:
      https://github.com/scalatron/scalatron/tree/master/Scalatron/doc/markdown
    */


class GeirBot {

  def respond(input: String): String = {
    val (opcode, params) = CommandParser(input)
    opcode match {
      case "React" =>
        val bot = new BotImpl(params)
        if (bot.generation == 0) {
          ControlFunction.forMaster(bot)
        } else {
          ControlFunction.forSlave(bot)
        }
        bot.toString
      case _ => "" // OK
    }

    /*
    opcode match {
      case "React" => {
        val nearestPlant: Option[XY] = bot.view.offsetToNearest(Cell.GoodPlant)
        val nearestEnemy: Option[XY] = bot.view.offsetToNearest(Cell.Enemy)
        val nearestWall: Option[XY] = bot.view.offsetToNearest(Cell.Wall)
        val me: Option[XY] = bot.view.offsetToNearest(Cell.You)



       val coordinate:XY =  (nearestPlant, nearestWall, me) match {
          case (Some(plant), Some(wall), Some(myself)) => if (myself.distanceTo(wall) > myself.distanceTo(plant)) wall.negate else plant.signum
          case (None, Some(wall), Some(myself)) =>   wall.negate
          case (Some(plant), None, Some(myself)) => plant.signum
          case _ => XY.fromDirection90(1)
       }
       bot.move(coordinate).toString

      */
    /*
       val viewString = params("view")
       val view = View(viewString)

       view.offsetToNearest(Cell.GoodPlant) match {
         case Some(offset) =>
           val unitOffset = offset.signum
           bot.move(unitOffset).say("Eat!!").toString
         case None => ""
       }
       view.offsetToNearest(Cell.Wall) match {
         case Some(offset) =>
           val unitOffset = offset.signum
           unitOffset.addToX(rnd.nextInt())
           bot.move(unitOffset).say("Wall").toString
         case None => ""
       }*/


  }


}


// -------------------------------------------------------------------------------------------------
// Framework
// -------------------------------------------------------------------------------------------------


trait Bot {
  // inputs
  def inputOrElse(key: String, fallback: String): String

  def inputAsIntOrElse(key: String, fallback: Int): Int

  def inputAsXYOrElse(keyPrefix: String, fallback: XY): XY

  def view: View

  def energy: Int

  def time: Int

  def generation: Int

  // outputs
  def move(delta: XY): Bot

  def say(text: String): Bot

  def status(text: String): Bot

  def spawn(offset: XY, params: (String, Any)*): Bot

  def set(params: (String, Any)*): Bot

  def log(text: String): Bot
}

trait MiniBot extends Bot {
  // inputs
  def offsetToMaster: XY

  // outputs
  def explode(blastRadius: Int): Bot
}


case class BotImpl(inputParams: Map[String, String]) extends MiniBot {
  // input
  def inputOrElse(key: String, fallback: String) = inputParams.getOrElse(key, fallback)

  def inputAsIntOrElse(key: String, fallback: Int) = inputParams.get(key).map(_.toInt).getOrElse(fallback)

  def inputAsXYOrElse(key: String, fallback: XY) = inputParams.get(key).map(s => XY(s)).getOrElse(fallback)

  val view = View(inputParams("view"))
  val energy = inputParams("energy").toInt
  val time = inputParams("time").toInt
  val generation = inputParams("generation").toInt

  def offsetToMaster = inputAsXYOrElse("master", XY.Zero)


  // output

  private var stateParams = Map.empty[String, Any]
  // holds "Set()" commands
  private var commands = ""
  // holds all other commands
  private var debugOutput = "" // holds all "Log()" output

  /** Appends a new command to the command string; returns 'this' for fluent API. */
  private def append(s: String): Bot = {
    commands += (if (commands.isEmpty) s else "|" + s); this
  }

  /** Renders commands and stateParams into a control function return string. */
  override def toString = {
    var result = commands
    if (!stateParams.isEmpty) {
      if (!result.isEmpty) result += "|"
      result += stateParams.map(e => e._1 + "=" + e._2).mkString("Set(", ",", ")")
    }
    if (!debugOutput.isEmpty) {
      if (!result.isEmpty) result += "|"
      result += "Log(text=" + debugOutput + ")"
    }
    result
  }

  def log(text: String) = {
    debugOutput += text + "\n"; this
  }

  def move(direction: XY) = append("Move(direction=" + direction + ")")

  def say(text: String) = append("Say(text=" + text + ")")

  def status(text: String) = append("Status(text=" + text + ")")

  def explode(blastRadius: Int) = append("Explode(size=" + blastRadius + ")")

  def spawn(offset: XY, params: (String, Any)*) =
    append("Spawn(direction=" + offset +
      (if (params.isEmpty) "" else "," + params.map(e => e._1 + "=" + e._2).mkString(",")) +
      ")")

  def set(params: (String, Any)*) = {
    stateParams ++= params; this
  }

  def set(keyPrefix: String, xy: XY) = {
    stateParams ++= List(keyPrefix + "x" -> xy.x, keyPrefix + "y" -> xy.y); this
  }
}


// -------------------------------------------------------------------------------------------------


/** Utility methods for parsing strings containing a single command of the format
  * "Command(key=value,key=value,...)"
  */
object CommandParser {
  /** "Command(..)" => ("Command", Map( ("key" -> "value"), ("key" -> "value"), ..}) */
  def apply(command: String): (String, Map[String, String]) = {
    /** "key=value" => ("key","value") */
    def splitParameterIntoKeyValue(param: String): (String, String) = {
      val segments = param.split('=')
      (segments(0), if (segments.length >= 2) segments(1) else "")
    }

    val segments = command.split('(')
    if (segments.length != 2)
      throw new IllegalStateException("invalid command: " + command)
    val opcode = segments(0)
    val params = segments(1).dropRight(1).split(',')
    val keyValuePairs = params.map(splitParameterIntoKeyValue).toMap
    (opcode, keyValuePairs)
  }
}


// -------------------------------------------------------------------------------------------------


/** Utility class for managing 2D cell coordinates.
  * The coordinate (0,0) corresponds to the top-left corner of the arena on screen.
  * The direction (1,-1) points right and up.
  */
case class XY(x: Int, y: Int) {
  override def toString = x + ":" + y

  def isNonZero = x != 0 || y != 0

  def isZero = x == 0 && y == 0

  def isNonNegative = x >= 0 && y >= 0

  def updateX(newX: Int) = XY(newX, y)

  def updateY(newY: Int) = XY(x, newY)

  def addToX(dx: Int) = XY(x + dx, y)

  def addToY(dy: Int) = XY(x, y + dy)

  def +(pos: XY) = XY(x + pos.x, y + pos.y)

  def -(pos: XY) = XY(x - pos.x, y - pos.y)

  def *(factor: Double) = XY((x * factor).intValue, (y * factor).intValue)

  def distanceTo(pos: XY): Double = (this - pos).length

  // Phythagorean
  def length: Double = math.sqrt(x * x + y * y) // Phythagorean

  def stepsTo(pos: XY): Int = (this - pos).stepCount

  // steps to reach pos: max delta X or Y
  def stepCount: Int = x.abs.max(y.abs) // steps from (0,0) to get here: max X or Y

  def signum = XY(x.signum, y.signum)

  def negate = XY(-x, -y)

  def negateX = XY(-x, y)

  def negateY = XY(x, -y)

  /** Returns the direction index with 'Right' being index 0, then clockwise in 45 degree steps. */
  def toDirection45: Int = {
    val unit = signum
    unit.x match {
      case -1 =>
        unit.y match {
          case -1 =>
            if (x < y * 3) Direction45.Left
            else if (y < x * 3) Direction45.Up
            else Direction45.UpLeft
          case 0 =>
            Direction45.Left
          case 1 =>
            if (-x > y * 3) Direction45.Left
            else if (y > -x * 3) Direction45.Down
            else Direction45.LeftDown
        }
      case 0 =>
        unit.y match {
          case 1 => Direction45.Down
          case 0 => throw new IllegalArgumentException("cannot compute direction index for (0,0)")
          case -1 => Direction45.Up
        }
      case 1 =>
        unit.y match {
          case -1 =>
            if (x > -y * 3) Direction45.Right
            else if (-y > x * 3) Direction45.Up
            else Direction45.RightUp
          case 0 =>
            Direction45.Right
          case 1 =>
            if (x > y * 3) Direction45.Right
            else if (y > x * 3) Direction45.Down
            else Direction45.DownRight
        }
    }
  }

  def rotateCounterClockwise45 = XY.fromDirection45((signum.toDirection45 + 1) % 8)

  def rotateCounterClockwise90 = XY.fromDirection45((signum.toDirection45 + 2) % 8)

  def rotateClockwise45 = XY.fromDirection45((signum.toDirection45 + 7) % 8)

  def rotateClockwise90 = XY.fromDirection45((signum.toDirection45 + 6) % 8)


  def wrap(boardSize: XY) = {
    val fixedX = if (x < 0) boardSize.x + x else if (x >= boardSize.x) x - boardSize.x else x
    val fixedY = if (y < 0) boardSize.y + y else if (y >= boardSize.y) y - boardSize.y else y
    if (fixedX != x || fixedY != y) XY(fixedX, fixedY) else this
  }
}


object XY {
  /** Parse an XY value from XY.toString format, e.g. "2:3". */
  def apply(s: String): XY = {
    val a = s.split(':'); XY(a(0).toInt, a(1).toInt)
  }

  val Zero = XY(0, 0)
  val One = XY(1, 1)

  val Right = XY(1, 0)
  val RightUp = XY(1, -1)
  val Up = XY(0, -1)
  val UpLeft = XY(-1, -1)
  val Left = XY(-1, 0)
  val LeftDown = XY(-1, 1)
  val Down = XY(0, 1)
  val DownRight = XY(1, 1)

  def fromDirection45(index: Int): XY = index match {
    case Direction45.Right => Right
    case Direction45.RightUp => RightUp
    case Direction45.Up => Up
    case Direction45.UpLeft => UpLeft
    case Direction45.Left => Left
    case Direction45.LeftDown => LeftDown
    case Direction45.Down => Down
    case Direction45.DownRight => DownRight
  }

  def fromDirection90(index: Int): XY = index match {
    case Direction90.Right => Right
    case Direction90.Up => Up
    case Direction90.Left => Left
    case Direction90.Down => Down
  }

  def apply(array: Array[Int]): XY = XY(array(0), array(1))
}


object Direction45 {
  val Right = 0
  val RightUp = 1
  val Up = 2
  val UpLeft = 3
  val Left = 4
  val LeftDown = 5
  val Down = 6
  val DownRight = 7
}


object Direction90 {
  val Right = 0
  val Up = 1
  val Left = 2
  val Down = 3
}


// -------------------------------------------------------------------------------------------------


case class View(cells: String) {
  val size = math.sqrt(cells.length).toInt
  val center = XY(size / 2, size / 2)

  def apply(relPos: XY) = cellAtRelPos(relPos)

  def indexFromAbsPos(absPos: XY) = absPos.x + absPos.y * size

  def absPosFromIndex(index: Int) = XY(index % size, index / size)

  def absPosFromRelPos(relPos: XY) = relPos + center

  def cellAtAbsPos(absPos: XY) = cells.charAt(indexFromAbsPos(absPos))

  def indexFromRelPos(relPos: XY) = indexFromAbsPos(absPosFromRelPos(relPos))

  def relPosFromAbsPos(absPos: XY) = absPos - center

  def relPosFromIndex(index: Int) = relPosFromAbsPos(absPosFromIndex(index))

  def cellAtRelPos(relPos: XY) = cells.charAt(indexFromRelPos(relPos))

  def offsetToNearest(cell: Cell) = {

    val matchingXY = cells.view.zipWithIndex.filter(_._1 == cell.char)
    if (matchingXY.isEmpty)
      None
    else {
      val nearest = matchingXY.map(p => relPosFromIndex(p._2)).minBy(_.length)
      Some(nearest)
    }
  }
}

abstract class Cell(val char: Char)

object Cell {

  case object Unknown extends Cell('?')

  case object Empty extends Cell('_')

  case object Wall extends Cell('W')

  case object You extends Cell('M')

  case object Enemy extends Cell('m')

  case object YourSlave extends Cell('S')

  case object EnemySlave extends Cell('s')

  case object GoodPlant extends Cell('P')

  case object BadPlant extends Cell('p')

  case object GoodBeast extends Cell('B')

  case object BadBeast extends Cell('b')

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
import scala.util.Random

class ControlFunctionFactory {
def create = new ControlFunction().respond _
}
class ControlFunction {
  val rnd = new Random(System.currentTimeMillis())

  def respond(input: String) = {
    val command = CommandParser(input)

    command match {
      case React(generation, time, viewStr, energy, name, params) => {
        val view = View(viewStr)
        val enemy = view.offsetToNearest('m')
        val enemyBot = view.offsetToNearest('s')

        generation match {
          case 0 => { // Main bot
            def shootMissile: Boolean = {
              energy.toInt > 200 && time > 10 && (
                //rnd.nextInt(100) < 2 |
                enemy.exists(view.center.distanceTo(_)<6) |
                enemyBot.exists(view.center.distanceTo(_)<3)
              )
            }
            val shoot = if (shootMissile) {
              val dir = nonDangerousAlternative(view, Coord(1, 0))
              "|Spawn(direction="+dir+")"
            } else ""
            val beast = view.offsetToNearest('B')
            val plant = view.offsetToNearest('P')

            (beast, plant) match {
              case (Some(beastO), Some(plantO)) =>
                val offset = if (view.center.distanceTo(beastO) + 2 < view.center.distanceTo(plantO)) beastO else plantO
                val unitOffset = offset.signum
                val heading = nonDangerousAlternative(view, unitOffset)
                "Move(direction=" + heading + ")|Set(heading="+heading+")"+shoot
              case (Some(offset), None) =>
                val unitOffset = offset.signum
                val heading = nonDangerousAlternative(view, unitOffset)
                "Move(direction=" + heading + ")|Set(heading="+heading+")"+shoot
              case (None, Some(offset)) =>
                val unitOffset = offset.signum
                val heading = nonDangerousAlternative(view, unitOffset)
                "Move(direction=" + heading + ")|Set(heading="+heading+")"+shoot
              case (None, None) =>
                val dir = params.get("heading").map(Coord.parse).getOrElse(Coord(1, 0))
                val heading = nonDangerousAlternative(view, dir)
                "Move(direction=" + heading + ")|Set(heading="+heading+")"+shoot
            }

          }
          case _ => { // Missile!!
            (enemy, enemyBot) match {
              case (Some(enemyO), _) if view.center.distanceTo(enemyO) <= 4 => "Explode(size="+view.center.distanceTo(enemyO)+")"
              case (_, Some(enemyBotO)) if view.center.distanceTo(enemyBotO) <= 2 => "Explode(size=2)"
              case (Some(enemyO), _) => {
                val heading = nonDangerousAlternative(view, enemyO.signum)
                "Move(direction=" + heading + ")|Set("+name+"_heading"++"="+heading+")"
              }
              case _ => {
                val dir = params.get(name+"_heading").map(Coord.parse).getOrElse(Coord(1, 0))
                val heading = nonDangerousAlternative(view, dir)
                "Move(direction=" + heading + ")|Set("+name+"_heading="+heading+")"
              }
            }
          }
        }
      }
      case _ => "Status(text=fiskefisk)"
    }
  }


  def nonDangerousAlternative(view: View, dir: Coord): Coord = {
    def getNewDir = nonDangerousAlternative(view, Coord(Random.nextInt(3)-1, Random.nextInt(3)-1))
    val newDir = view.Relative(dir) match {
      case Cell.Wall => getNewDir
      case Cell.BadBeast => getNewDir
      case Cell.BadPlant => getNewDir
      case Cell.YourSlave => getNewDir
      case _ => dir
    }
    newDir match {
      case Coord(0, 0) => nonDangerousAlternative(view, Coord(Random.nextInt(3)-1, Random.nextInt(3)-1))
      case _ => newDir
    }
  }
}
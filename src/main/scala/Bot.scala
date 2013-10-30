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
              val dir = nonDangerousAlternative(view, Heading.East)
              Some(Spawn(dir))
            } else None
            val beast = view.offsetToNearest('B')
            val plant = view.offsetToNearest('P')

            (beast, plant) match {
              case (Some(beastO), Some(plantO)) =>
                val offset = if (view.center.distanceTo(beastO) + 2 < view.center.distanceTo(plantO)) beastO else plantO
                val heading = nonDangerousAlternative(view, offset.heading)
                val headingCmd = Some(Set(Map("heading"->heading.toString)))
                Move(heading) and (headingCmd :: shoot :: Nil)
              case (Some(offset), None) =>
                val heading = nonDangerousAlternative(view, offset.heading)
                val headingCmd = Some(Set(Map("heading"->heading.toString)))
                Move(heading) and (headingCmd :: shoot :: Nil)
              case (None, Some(offset)) =>
                val heading = nonDangerousAlternative(view, offset.heading)
                val headingCmd = Some(Set(Map("heading"->heading.toString)))
                Move(heading) and (headingCmd :: shoot :: Nil)
              case (None, None) =>
                val dir = params.get("heading").map(Heading.parse).getOrElse(Heading.East)
                val heading = nonDangerousAlternative(view, dir)
                val headingCmd = Some(Set(Map("heading"->heading.toString)))
                Move(heading) and (headingCmd :: shoot :: Nil)
            }

          }
          case _ => { // Missile!!
            (enemy, enemyBot) match {
              case (Some(enemyO), _) if view.center.distanceTo(enemyO) <= 4 => Explode(view.center.distanceTo(enemyO).toInt)
              case (_, Some(enemyBotO)) if view.center.distanceTo(enemyBotO) <= 2 => Explode(2)
              case (Some(enemyO), _) => {
                val heading = nonDangerousAlternative(view, enemyO.heading)
                val headingCmd = Some(Set(Map("heading"->heading.toString)))
                Move(heading) and (headingCmd :: Nil)
              }
              case _ => {
                val dir = params.get(name+"_heading").map(Coord.parse).getOrElse(Coord(1, 0))
                val heading = nonDangerousAlternative(view, dir.heading)
                val headingCmd = Some(Set(Map("heading"->heading.toString)))
                Move(heading) and (headingCmd :: Nil)
              }
            }
          }
        }
      }
      case _ => Status("Så rart...")
    }
  }


  def nonDangerousAlternative(view: View, dir: Heading): Heading = {
    def getNewDir = nonDangerousAlternative(view, Heading.random(rnd))
    view.Relative(dir) match {
      case Cell.Wall => getNewDir
      case Cell.BadBeast => getNewDir
      case Cell.BadPlant => getNewDir
      case Cell.YourSlave => getNewDir
      case _ => dir
    }
  }
}
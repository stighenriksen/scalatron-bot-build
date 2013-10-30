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
              energy > 200 && time > 10 && (
                enemy.exists(view.center.stepsTo(_)<30) |
                enemyBot.exists(view.center.stepsTo(_)<5)
              )
            }
            val shoot = if (shootMissile) {
              val dir = nonDangerousAlternative(view, enemy.map(_.heading).getOrElse(Heading.East))
              Some(Spawn(dir))
            } else None

            val beast = view.offsetToNearest('B')
            val plant = view.offsetToNearest('P')

            (beast, plant) match {
              case (Some(beastO), Some(plantO)) =>
                val offset = if (view.center.stepsTo(beastO) + 2 < view.center.stepsTo(plantO)) beastO else plantO
                val heading = nonDangerousAlternative(view, offset.heading)
                val setHeading = Some(Set(Map("heading"->heading.toString)))
                Move(heading) and (setHeading :: shoot :: Nil)
              case (Some(offset), None) =>
                val heading = nonDangerousAlternative(view, offset.heading)
                val setHeading = Some(Set(Map("heading"->heading.toString)))
                Move(heading) and (setHeading :: shoot :: Nil)
              case (None, Some(offset)) =>
                val heading = nonDangerousAlternative(view, offset.heading)
                val setHeading = Some(Set(Map("heading"->heading.toString)))
                Move(heading) and (setHeading :: shoot :: Nil)
              case (None, None) =>
                val dir = params.get("heading").map(Heading.parse).getOrElse(Heading.East)
                val heading = nonDangerousAlternative(view, dir)
                val setHeading = Some(Set(Map("heading"->heading.toString)))
                Move(heading) and (setHeading :: shoot :: Nil)
            }

          }
          case _ => { // Missile!!
            (enemy, enemyBot) match {
              case (Some(enemyO), _) if view.center.distanceTo(enemyO) <= 4 =>
                "Explode(size=4)"
              case (_, Some(enemyBotO)) if view.center.distanceTo(enemyBotO) <= 4 =>
                "Explode(size=4)"
              case (Some(enemyO), _) => {
                val heading = nonDangerousAlternative(view, enemyO.heading)
                val setHeading = Some(Set(Map(name+"heading"->heading.toString)))
                val ret = Move(heading) and (setHeading :: Nil)
                ret
              }
              case _ => {
                val dir = params.get(name+"_heading").map(Heading.parse).getOrElse(Heading.East)
                val heading = nonDangerousAlternative(view, dir)
                val setHeading = Some(Set(Map(name+"heading"->heading.toString)))
                Move(heading) and (setHeading :: Nil)
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
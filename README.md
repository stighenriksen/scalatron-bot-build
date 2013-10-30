scalatron-bot-build
===================

This project aims to help with building and deploying a [Scalatron](http://scalatron.github.io/) bot to
a local and a remote Scalatron server.

## Build and deploy local bot:

1. Run './sbt' from the command line. All following commands are expected to be entered in this
  sbt session.
2. Run 'gen-idea', and then open the project in Intellij
3. Open Bot.scala and try to change the "Hello Scalatron!" text to make the bot output something else. For instance:
```
  "Status(text=My first bot!)"
```
3. Go back to sbt and run 'start'. This will start a local Scalatron server.
4. Run 'deploy-local'. Enter the name of your bot and press enter.
5. Open the Scalatron display window and press 'r' to refresh,
   and verify that you have a bot in the arena with the name you entered.
6. Go back to IntelliJ (or alternatively, your favourite editor), make changes and run 'deploy-local' again when ready.
7. To delete all the local bots running around (except the 'Reference' bot), run 'delete-bots'.
8. To stop the local Scalatron server, run 'stop'

## Local debugging:
The 'start' command runs the Scalatron server in debugging mode, listening to port 5005. For instance,
if you want to debug in intellij, add a new "Remote" run configuration with default settings, press
"debug", set your breakpoints and you're good to go!

## How to build and deploy bots to a remote Scalatron server (having verified that local deploy works):

1. Create a user account on the remote server (or get the admin to do it)
2. Set the remote 'host' setting in config.json to the the IP or URL of the running server. Also,
   update the 'port' setting if the remote server is running on something else than 8080.
   For instance, if the remote host and port is 1.2.3.4:8080, make sure the remote section
   of the config looks like this:
   ```
     "remote" : {
        "host" : 1.2.3.4",
         "port": 8080
     }
   ```

3. Run the sbt command 'deploy-remote', and enter your account name and password. In the next round of the remote Scalatron tournament, you should see your bot!

## Troubleshooting:
- If all you get is a gray frame when the local Scalatron server starts, try changing the Java version used by editing build.sbt.

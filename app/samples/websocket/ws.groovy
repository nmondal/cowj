_log.info(event.type)

switch (event.type){
    case "connect" -> event.send("Welcome!", 3 )
    case "message" -> event.send("ya!", 3 )
    case "error" ->  event.data.printStackTrace()
}

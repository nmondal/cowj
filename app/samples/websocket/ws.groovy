_log.info(event.type)

switch (event.type){
    case "connect" -> event.session.getRemote().sendString("Welcome!")
    case "message" -> event.session.getRemote().sendString("ya!")
    case "error" ->  event.data.printStackTrace()
}

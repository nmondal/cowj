_log.info(event.type)
if ( event.type == "connect"){
    event.session.getRemote().sendString("Welcome!");
} else if ( event.type == "message") {
    event.session.getRemote().sendString("ya!");
}
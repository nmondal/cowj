import cowj.ScriptableSocket
// just ping current time
dt = "" + new Date()
_log.info("Date is {}", dt )
ScriptableSocket.broadcast("/ws", dt )

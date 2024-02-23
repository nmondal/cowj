package cowj;

import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


@WebSocket
public final class ScriptableSocket {

    static class SocketEvent{

        public final String type;
        public final Session session;

        public final Object data;
        public final int code;

        private SocketEvent(String type, Session session, Object data, int code){
            this.type = type;
            this.session = session;
            this.data = data;
            this.code = code;
        }

    }

    /**
     * Logger for the Cowj ScriptableSocket
     */
    Logger logger = LoggerFactory.getLogger(ScriptableSocket.class);

    public static final Map<String, Set<Session>> SESSIONS = new ConcurrentHashMap<>();

    private final Scriptable scriptable;

    public final String path;

    private ScriptableSocket(String wsPath, String handler){
        this.path = wsPath;
        SESSIONS.put(wsPath, Collections.synchronizedSet(new HashSet<>()));
        scriptable = Scriptable.UNIVERSAL.create( "ws://" + wsPath, handler);
    }

    public static ScriptableSocket socket(String wsPath, String handler){
        return new ScriptableSocket(wsPath, handler);
    }

    public static final String EVENT = "event" ;
    public static final String EVENT_CONNECT = "connect" ;
    public static final String EVENT_CLOSED = "closed" ;
    public static final String EVENT_MESSAGE = "message" ;
    public static final String EVENT_ERROR = "error" ;
    public static final String EVENT_FRAME = "frame" ;

    private void handleEvent( SocketEvent event){
        Bindings bindings = new SimpleBindings();
        bindings.put(EVENT, event);
        try {
            scriptable.exec(bindings);
        }catch (Throwable th){
            logger.error("Error processing event  {} due to {}",event.type, th.toString());
        }
    }

    @OnWebSocketConnect
    public void connected(Session session) {
        logger.debug("connect - {}", session);
        SESSIONS.get(path).add(session);
        handleEvent( new SocketEvent(EVENT_CONNECT, session, null, -1));
    }

    @OnWebSocketClose
    public void closed(Session session, int statusCode, String reason) {
        logger.debug("closed - {} , code {}, reason {}", session, statusCode, reason );
        handleEvent( new SocketEvent(EVENT_CLOSED, session, reason, statusCode));
        SESSIONS.get(path).remove(session);
    }

    @OnWebSocketFrame
    public void frame(Session session, Frame frame){
        logger.debug("frame - {} , frame {}", session, frame);
        handleEvent( new SocketEvent(EVENT_FRAME, session, frame, -1));
    }

    @OnWebSocketMessage
    public void message(Session session, String message)  {
        logger.debug("message - {} , message {}", session, message );
        handleEvent( new SocketEvent(EVENT_MESSAGE, session, message, -1));
    }

    @OnWebSocketError
    public void error(Session session, Throwable error){
        logger.debug("error - {} , message {}", session, error.toString() );
        handleEvent( new SocketEvent(EVENT_ERROR, session, error, -1));
    }
}

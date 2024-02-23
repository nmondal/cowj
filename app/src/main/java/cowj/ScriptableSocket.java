package cowj;

import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Template jetty based WebSocket implementation
 * This wraps around a Scriptable
 */
@WebSocket
public final class ScriptableSocket {

    /**
     * A Payload for Any WebSocket event
     */
    static class SocketEvent{

        /**
         * Type of the event, one of
         * connect, closed, message, error, frame
         * As verbatim
         */
        public final String type;

        /**
         * A jetty WebSocket Session
         */
        public final Session session;

        /**
         * Either null, String, Frame, Throwable
         * Based on the event type
         * closed, message : String
         * frame : Frame
         * error : Throwable
         */
        public final Object data;

        /**
         * Code , always -1, except in close, when it depicts the code for closure
         */
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

    /**
     * A holder for all sessions across all WebSocket connections across paths
     */
    public static final Map<String, Set<Session>> SESSIONS = new ConcurrentHashMap<>();

    private final Scriptable scriptable;

    /**
     * Which path mapping this handler has
     */
    public final String path;

    private ScriptableSocket(String wsPath, String handler){
        this.path = wsPath;
        SESSIONS.put(wsPath, Collections.synchronizedSet(new HashSet<>()));
        scriptable = Scriptable.UNIVERSAL.create( "ws://" + wsPath, handler);
    }

    /**
     * Creates in instance from path and handler path
     * @param wsPath web socket path route
     * @param handler Scriptable handler path
     * @return an instance of ScriptableSocket to handle the websocket
     */
    public static ScriptableSocket socket(String wsPath, String handler){
        return new ScriptableSocket(wsPath, handler);
    }

    /**
     * Event Variable Key for the Scriptable
     */
    public static final String EVENT = "event" ;

    /**
     * Connect Event
     */
    public static final String EVENT_CONNECT = "connect" ;

    /**
     * Closed Event
     */
    public static final String EVENT_CLOSED = "closed" ;

    /**
     * Message Event
     */
    public static final String EVENT_MESSAGE = "message" ;

    /**
     * Error Event
     */
    public static final String EVENT_ERROR = "error" ;

    /**
     * Frame Event
     */
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

    /**
     * Template method for passing 'connected' event to Scriptable
     * @param session jetty WebSocket Session
     */
    @OnWebSocketConnect
    public void connected(Session session) {
        logger.debug("connect - {}", session);
        SESSIONS.get(path).add(session);
        handleEvent( new SocketEvent(EVENT_CONNECT, session, null, -1));
    }

    /**
     * Template method for passing 'closed' event to Scriptable
     * @param session jetty WebSocket Session
     * @param statusCode code for why connection was closed
     * @param reason String - human description for why connection was closed
     */

    @OnWebSocketClose
    public void closed(Session session, int statusCode, String reason) {
        logger.debug("closed - {} , code {}, reason {}", session, statusCode, reason );
        handleEvent( new SocketEvent(EVENT_CLOSED, session, reason, statusCode));
        SESSIONS.get(path).remove(session);
    }

    /**
     * Template method for passing 'frame' event to Scriptable
     * @param session jetty WebSocket Session
     * @param frame a jetty Frame
     */
    @OnWebSocketFrame
    public void frame(Session session, Frame frame){
        logger.debug("frame - {} , frame {}", session, frame);
        handleEvent( new SocketEvent(EVENT_FRAME, session, frame, -1));
    }

    /**
     * Template method for passing 'message' event to Scriptable
     * @param session jetty WebSocket Session
     * @param message the String which is received from the client
     */
    @OnWebSocketMessage
    public void message(Session session, String message)  {
        logger.debug("message - {} , message {}", session, message );
        handleEvent( new SocketEvent(EVENT_MESSAGE, session, message, -1));
    }

    /**
     * Template method for passing 'error' event to Scriptable
     * @param session jetty WebSocket Session
     * @param error Throwable that was encountered
     */
    @OnWebSocketError
    public void error(Session session, Throwable error){
        logger.debug("error - {} , message {}", session, error.toString() );
        handleEvent( new SocketEvent(EVENT_ERROR, session, error, -1));
    }
}

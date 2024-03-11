package bgu.spl.net.impl.tftp;
import java.util.concurrent.ConcurrentHashMap;
import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;

/*
 * Implement Connections<T> to hold a list of the new ConnectionHandler interface for each active client. Use
it to implement the interface functions. Notice that given a Connections implementation, any protocol
should run. This means that you keep your implementation of Connections on T.
 */

 //TODO: check if connectionId is needed to change to string username
public class ConnectionsImpl<T> implements Connections<T>{

    //list of the new ConnectionHandler interface for each active client
    private ConcurrentHashMap<Integer, ConnectionHandler<T>> connectionHandlers = new ConcurrentHashMap<>();

    public void connect(int connectionId, ConnectionHandler<T> handler){
        //add an client connectionId to active client map
        //Note: you can change the return value to boolean if you want.
        connectionHandlers.put(connectionId, handler);
    }

    public boolean send(int connectionId, T msg){
        //sends a message T to the client represented by the given connectionId.
        if(connectionHandlers.containsKey(connectionId)){
            connectionHandlers.get(connectionId).send(msg);
            return true;
        }
        return false;
    }

    public void disconnect(int connectionId){
        //Removes an active client connectionId from the map
        connectionHandlers.remove(connectionId);
    }

    /* ------------------------------ Added methods ----------------------------- */

    //broadcast the message to all active clients.
    public void broadcast(T msg){
        //TODO: synchronize?
        for(ConnectionHandler<T> handler : connectionHandlers.values()){
            handler.send(msg);
        }
    }
}

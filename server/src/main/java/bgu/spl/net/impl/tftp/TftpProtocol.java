package bgu.spl.net.impl.tftp;

import java.util.concurrent.ConcurrentHashMap;
import java.io.File;
import java.io.FileInputStream;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

class holder{
    static ConcurrentHashMap<Integer, Boolean> login_ids = new ConcurrentHashMap<>();
}

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

    private boolean shouldTerminate = false;

    private int connectionId;

    private Connections<byte[]> connections;


    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        //Initiate the protocol with the active connections structure of the server and saves the owner client’s connection id.
        this.connectionId = connectionId;
        this.connections = connections;
        this.shouldTerminate = false;
        holder.login_ids.put(connectionId, true);
    }

    private void rrq(byte[] message){
        //read request
        //use fileInputStream to read the file


    }

    private void wrq(byte[] message){
        //write request

        //check if the file exists
    }

    private void data(byte[] message){
        //data
    }

    private void ack(byte[] message){
        //ack

    }

    private void error(byte[] message){
        //error
        if(conv2bToShort(message[2],message[3]) == 0){
            //Not defined, see error message (if any)
        }
        else if(conv2bToShort(message[2],message[3]) == 1){
            //File not found – RRQ DELRQ of non-existing file
        }
        else if(conv2bToShort(message[2],message[3]) == 2){
            //Access violation – File cannot be written, read or deleted
        }
        else if(conv2bToShort(message[2],message[3]) == 3){
            //Disk full or allocation exceeded – No room in disk
        }
        else if(conv2bToShort(message[2],message[3]) == 4){
            //Illegal TFTP operation – Unknown Opcode
        }
        else if(conv2bToShort(message[2],message[3]) == 5){
            //File already exists – File name exists on WRQ.
        }
        else if(conv2bToShort(message[2],message[3]) == 6){
            //User not logged in – Any opcode received before Login completes
        }
        else if(conv2bToShort(message[2],message[3]) == 0){
            //User already logged in – Login username already connected
        }
        else{
            //invalid
        }
    }

    private void dirq(byte[] message){
        //directory listing request
    }

    private void logrq(byte[] message){
        //login request
    }

    private void delrq(byte[] message){
        //delete request
    }

    private void bcst(byte[] message){
        //broadcast file deleted or added
    }

    private void disc(byte[] message){
        //logout request
    }

    @Override
    public void process(byte[] message) {
        //As in MessagingProtocol, processes a given message. Unlike MessagingProtocol,
        // responses are sent via the connections object send functions (if needed)
        if(conv2bToShort(message[0],message[1]) == 1){
            //read request
            rrq(message);
        }
        else if(conv2bToShort(message[0],message[1]) == 2){
            //write request
            wrq(message);
        }
        else if(conv2bToShort(message[0],message[1]) == 3){
            //data
            data(message);
        }
        else if(conv2bToShort(message[0],message[1]) == 4){
            //ack
            ack(message);
        }
        else if(conv2bToShort(message[0],message[1]) == 5){
            //error
            error(message);
        }
        else if(conv2bToShort(message[0],message[1]) == 6){
            //directory listing request
            dirq(message);
        }
        else if(conv2bToShort(message[0],message[1]) == 7){
            //login request
            logrq(message);
        }
        else if(conv2bToShort(message[0],message[1]) == 8){
            //delete request
            delrq(message);
        }
        else if(conv2bToShort(message[0],message[1]) == 9){
            //broadcast file deleted or added
            bcst(message);
        }
        else if(conv2bToShort(message[0],message[1]) == 10){
            //logout request
            disc(message);
        }
        else{
            //invalid

        }
    }

    @Override
    public boolean shouldTerminate() {
        //true if the connection should be terminated
        if(shouldTerminate){
            holder.login_ids.remove(connectionId);
            this.connections.disconnect(connectionId);
        }
        return shouldTerminate;
    }

    private byte[] convShortTo2b(short num){
        // converting short to 2 byte array
        byte[] a_bytes = new byte []{(byte)( num >> 8), (byte)(num & 0xff)};
        return a_bytes;
    }

    private short conv2bToShort(byte b1 , byte b2){
    // converting 2 byte array to a short
    short b_short = (short)(((short) b1) << 8 | (short) (b2));
    return b_short;
    }
}

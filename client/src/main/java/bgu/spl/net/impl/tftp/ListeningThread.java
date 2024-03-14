package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;

public class ListeningThread extends Thread {
    /*
    Reads packets from the socket and displays messages or sends packets in return.
     */
    private final BufferedInputStream in;
    private final TftpEncoderDecoder encdec;
    private final TftpClientProtocol protocol;
    private final TftpClient client;
    public ListeningThread(TftpClient client, BufferedInputStream in, BufferedOutputStream out) {
        this.client = client;
        this.in = in;
        this.encdec = client.encdec;
        this.protocol = new TftpClientProtocol(client.keyboardThread,out);
    }

    @Override
    public void run() {
        try {
            int read;
            Socket socket;
            while (!shouldTerminate()) {
                synchronized (this) {
                    if(shouldTerminate() ||  !((read = in.read()) >= 0))
                        break;
                }
                byte[] nextMessage = encdec.decodeNextByte((byte) read);
                if (nextMessage != null) {
                    System.out.println("Message from server: " + Arrays.toString(nextMessage));
                    protocol.process(nextMessage);
                }
            }

        }catch (SocketException e) {
            if(!shouldTerminate())
                e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }

    }

    private boolean shouldTerminate() {
        return client.shouldTerminate();
    }
}

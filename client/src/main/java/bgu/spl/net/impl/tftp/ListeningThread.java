package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class ListeningThread extends Thread {
    /*
    Reads packets from the socket and displays messages or sends packets in return.
     */
    private final BufferedInputStream in;
    private final TftpEncoderDecoder encdec;
    TftpClientProtocol protocol;
    public ListeningThread(KeyboardThread keyboardThread, BufferedInputStream in, BufferedOutputStream out, TftpEncoderDecoder encdec) {
        this.in = in;
        this.encdec = encdec;
        this.protocol = new TftpClientProtocol(keyboardThread,out);
    }

    @Override
    public void run() {
        try {
            int read;
            while ((read = in.read()) >= 0) {
                byte[] nextMessage = encdec.decodeNextByte((byte) read);
                if (nextMessage != null) {
                    System.out.println("Message from server: " + Arrays.toString(nextMessage));
                    protocol.process(nextMessage);
                }
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
}

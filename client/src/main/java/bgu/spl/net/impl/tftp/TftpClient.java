package bgu.spl.net.impl.tftp;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;

import static java.lang.System.out;

public class TftpClient {
    //TODO: implement the main logic of the client, when using a thread per client the main logic goes here
    KeyboardThread keyboardThread;
    ListeningThread listeningThread;
    TftpEncoderDecoder encdec = new TftpEncoderDecoder();
    int currentOpcode = -1;
    Socket socket;
    private boolean terminate = false;
    public static void main(String[] args) {
        if (args.length < 2) {
            out.println("you must supply two arguments: host, port");
            System.exit(1);
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        TftpClient client = new TftpClient();
        /*
            Keyboard thread - reads commands from the keyboard and sends packets to the server defined by the command.
            Listening thread - reads packets from the socket and displays messages or sends packets in return.
        */
        try {
            client.socket = new Socket(host, port);
            BufferedInputStream in = new BufferedInputStream(client.socket.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(client.socket.getOutputStream());

            client.keyboardThread = new KeyboardThread(client,out);
            client.listeningThread = new ListeningThread(client,in,out);

            client.listeningThread.start();
            client.keyboardThread.start();


//            client.listeningThread.join();
//            client.keyboardThread.join();
            /*out.println("sending message to server");
            out.write(args[1]);
            out.newLine();
            out.flush();

            out.println("awaiting response");
            String line = in.readLine();
            out.println("message from server: " + line);*/
//            if(client.disconnected)
//                socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }



    }

    public boolean shouldTerminate() {
        return terminate;
    }

    public void disconnect( ) {
        if(socket == null) return;
        try {
            terminate = true;
//            out.println(terminate);
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

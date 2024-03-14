package bgu.spl.net.impl.tftp;

import java.io.*;
import java.net.Socket;

public class TftpClient {
    //TODO: implement the main logic of the client, when using a thread per client the main logic goes here
    KeyboardThread keyboardThread;
    ListeningThread listeningThread;
    TftpEncoderDecoder encdec = new TftpEncoderDecoder();
    int currentOpcode = -1;
    Socket socket;
    private boolean terminate = false;
    public static void main(String[] args) {
        // if (args.length < 2) {
        //     out.println("you must supply two arguments: host, port");
        //     System.exit(1);
        // }

        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 7777;
        TftpClient client = new TftpClient();

        try {
            client.socket = new Socket(host, port);
            System.out.println("Connected to the server!");
            BufferedInputStream in = new BufferedInputStream(client.socket.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(client.socket.getOutputStream());

            client.keyboardThread = new KeyboardThread(client,out);
            client.listeningThread = new ListeningThread(client,in,out);

            client.listeningThread.start();
            client.keyboardThread.start();

            client.listeningThread.join();
            client.keyboardThread.join();

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
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}

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
        //try with resources
        try {
            Socket socket = new Socket(host, port);
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());

            client.keyboardThread = new KeyboardThread(out,client.encdec);
            client.listeningThread = new ListeningThread(client.keyboardThread, in, out, client.encdec);

            client.listeningThread.start();
            client.keyboardThread.start();

            /*out.println("sending message to server");
            out.write(args[1]);
            out.newLine();
            out.flush();

            out.println("awaiting response");
            String line = in.readLine();
            out.println("message from server: " + line);*/
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}

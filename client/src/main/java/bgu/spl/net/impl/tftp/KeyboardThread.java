package bgu.spl.net.impl.tftp;

import sun.jvm.hotspot.interpreter.BytecodeStream;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Scanner;

public class KeyboardThread extends Thread {
    /*
    Reads commands from the keyboard and sends packets to the server defined by the command.
     */
    private Scanner in;
    private TftpEncoderDecoder encdec;
    private BufferedOutputStream out;
    private File rrqFile;
    private File wrqFile;
    private int error = -1;
    private boolean terminate;
    private final TftpClient client;

    public void setError(int error) {
        this.error = error;
    }

    public KeyboardThread(TftpClient client, BufferedOutputStream out) {
        this.client = client;
        this.out = out;
        this.encdec = client.encdec;
        this.in = new Scanner(System.in);
        terminate = false;
    }

    public void processLine(String line) {
        String[] command_arr = line.split(" ");
        String restOfLine = line.substring(command_arr[0].length())
                .trim();//removes spaces at the start and the end of the string

        switch(command_arr[0]) {
            case "LOGRQ":
                command_logrq(restOfLine);
                break;
            case "DELRQ":
                command_delrq(command_arr[1]);
                break;
            case "RRQ":
                command_rrq(command_arr[1]);
                break;
            case "WRQ":
                command_wrq(command_arr[1]);
                break;
            case "DIRQ":
                command_dirq();
                break;
            case "DISC":
                command_disc();
                break;
        }
    }

    /*
        Takes a list of byte arrays and concatenates them into a single long byte array, one after the other.
     */
    private byte[] concat_byte_arrays(byte[][] bytes) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for(int i = 0; i < bytes.length; i++)
            outputStream.write(bytes[i]);
        return outputStream.toByteArray();
    }

    /*
        Sends message with a 0 at the end
     */
    private void sendMessage(byte[] opcode, String s) {
        byte[] s_in_bytes = s.getBytes(StandardCharsets.UTF_8);
        //concat those two byte arrays into one message byte array
        try {
            byte[] message = concat_byte_arrays(new byte[][]{opcode, s_in_bytes, new byte[]{0}});
            send(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void command_logrq(String username) {
        sendMessage(new byte[]{0,7},username);
        synchronized (this) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    private void command_delrq(String filename) {
        sendMessage(new byte[]{0,8},filename);
    }

    public File getRRQFile() {
        return this.rrqFile;
    }

    public void setRRQFileNull() {
        this.rrqFile = null;
    }

    public File getWRQFile() {
        return this.wrqFile;
    }

    public void setWRQFileNull() {
        this.wrqFile = null;
    }

    private void command_rrq(String filename) {
        try {
            File file = new File(filename);
            if(file.createNewFile()) {
                //file created
                sendMessage(new byte[]{0,1},filename);
                rrqFile = file;
                while(rrqFile != null) {
                    synchronized (this) {
                        System.out.println("Waiting");
                        this.wait();
                    }
                    this.notifyAll();
                }
//                rrqFile = null;
            }
            else {
                //file already exists
                System.out.println("File already exists");
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    private void command_wrq(String filename) {
        File file = new File(filename);
        if(file.exists()) {
            //file exists
            sendMessage(new byte[]{0,2},filename);
            wrqFile = file;

//                rrqFile = null;
        }
        else {
            //file does not exist
            System.out.println("File does not exist");
        }
    }

    private void command_dirq() {
        send(new byte[]{0,6});
    }

    private void command_disc() {
        send(new byte[]{0,10});
        synchronized (this) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if(error == 1 || error == 0) {
            //TODO: check if there's an error what to do
            client.disconnect();
        }

        error = -1;
    }

    @Override
    public void run() {
        //waiting for user input
        while(!shouldTerminate()) {
            String line = in.nextLine();
            processLine(line);
        }
    }

    private boolean shouldTerminate() {
        return client.shouldTerminate();
    }

    public void send(byte[] message) {
        try {
            out.write(message);
            out.flush();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

}

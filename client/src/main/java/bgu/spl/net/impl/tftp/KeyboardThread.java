package bgu.spl.net.impl.tftp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
    private int error = -1;
    private boolean terminate;
    private final TftpClient client;

    private volatile short ack_block_number = 0;

    public void setACKBlockNumber(short block_number) {
        this.ack_block_number = block_number;
        error = 0;
    }

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
                command_delrq(restOfLine);
                break;
            case "RRQ":
                command_rrq(restOfLine);
                break;
            case "WRQ":
                command_wrq(restOfLine);
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

    public void onRRQFinish() {
        this.rrqFile = null;
        synchronized (this) {
            this.notifyAll();
        }
    }

    private boolean dirq = false;

    public boolean isDirq() {
        return dirq;
    }

    public void setDirq(boolean dirq) {
        this.dirq = dirq;
    }

    private void command_dirq() {
        send(new byte[]{0,6});
        try {
            synchronized (this) {
                this.wait();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
//        if(error == 0) {//no error
            dirq = true;
//        }
    }

    private void command_rrq(String filename) {
        try {
            File file = new File(filename);
            if(!file.isFile()) {
                // the file does not exist

                error = -1;
                rrqFile = file;
                sendMessage(new byte[]{0,1},filename);
                synchronized (this) {
                    this.wait();
                }
                if(error == 0)
                    file.createNewFile();
//
//                synchronized (this) {
//                    this.notifyAll();
//                }

            }
            else { // the file exists
                System.out.println("File already exists");
            }
        } catch(Exception e) {
            throw new RuntimeException(e);
        }

    }

    private void command_wrq(String filename) {
        //Check if file exist then send a WRQ packet and wait for ACK or ERROR packet to be received in the Listening thread. If received ACK start transferring the file.
        File file = new File(filename);
        if(file.exists()) {
            //file exists
            //send a WRQ packet
            sendMessage(new byte[]{0,2},filename);
            //wait for ACK or ERROR packet to be received in the Listening thread
            try {
                synchronized (this) {
//                    System.out.println("Waiting WRQ");
                    this.wait();
                }
                if(error == 0) { //no error
                    new SendDataUtil(this).sendData(file);
                }

            } catch(InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
        else {
            //file does not exist
            System.out.println("File does not exist");
        }
    }

    class SendDataUtil {
        byte[] file_data = null;
        int bytes_to_write = -1,
                current_pos = 0;
        final int MAX_DATA_SECTION_SIZE = 512;
        private final KeyboardThread keyboardThread;

        public SendDataUtil(KeyboardThread keyboardThread) {
            this.keyboardThread = keyboardThread;
        }

        private void sendData(File file) throws IOException, InterruptedException {
            final int MAX_DATA_SECTION_SIZE = 512;
            file_data = Files.readAllBytes(file.toPath());
            bytes_to_write = file_data.length;

            while (bytes_to_write > 0) {
                int bytes_written = 0;
//                System.out.println(bytes_to_write);
                synchronized (keyboardThread) {
                    bytes_written = sendSingleDataMessage(++keyboardThread.ack_block_number);
                    keyboardThread.wait();
                }
                bytes_to_write -= bytes_written;
                if(error != 0)
                    break;
            }

            //completed the transfer
            System.out.println("WRQ " + file.getName() + " complete");
        }

        /*
            Returns how many bytes were written
         */
        private int sendSingleDataMessage(short block_number) throws IOException {
            byte[] message;
            int packet_size = Math.min(MAX_DATA_SECTION_SIZE, bytes_to_write);
            message = new byte[2 + 2 + 2 + packet_size];
            byte[] opcode_bytes = {0, 3};
            byte[] packet_size_bytes = convShortTo2b((short) packet_size);
//            System.out.println("Block number: " + block_number);
            byte[] block_number_bytes = convShortTo2b(block_number);
            byte[] data = Arrays.copyOfRange(file_data, current_pos, current_pos + packet_size);
            message = concat_byte_arrays(new byte[][]{opcode_bytes, packet_size_bytes, block_number_bytes, data});
            send(message);
//            System.out.println("Sending data: " + Arrays.toString(message));
            current_pos += packet_size;

            return packet_size;
        }
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

        client.disconnect();


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

    private synchronized void send(byte[] message) {
        try {
            out.write(message);
            out.flush();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private byte[] convShortTo2b(short num){
        // converting short to 2 byte array
        byte[] a_bytes = new byte[2];
        a_bytes[0] = (byte) (num >> 8);
        a_bytes[1] = (byte) (num);
        return a_bytes;
    }

}

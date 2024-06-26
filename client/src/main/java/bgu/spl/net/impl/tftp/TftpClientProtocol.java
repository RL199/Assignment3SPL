package bgu.spl.net.impl.tftp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TftpClientProtocol {
    private final KeyboardThread keyboardThread;
    private final BufferedOutputStream out;
    public TftpClientProtocol(KeyboardThread keyboardThread, BufferedOutputStream out) {
        this.keyboardThread = keyboardThread;
        this.out = out;
    }

    public void process(byte[] message) {
        if(!shouldTerminate()) {
//            System.out.println("Processing message: " + Arrays.toString(message));
            byte[] opcode = new byte[]{message[0],message[1]};
            byte[] message_without_opcode = Arrays.copyOfRange(message,2,message.length);
            switch(conv2bToShort(opcode)) {
                case 4:
                    receiveACK(message_without_opcode);
                    break;
                case 3:
                    data(message_without_opcode);
                    break;
                case 9:
                    bcast(message_without_opcode);
                    break;
                case 5:
                    error(message_without_opcode);
                    break;
            }
        }
    }

    private void notifyKeyboardThread(boolean error) {
        synchronized (keyboardThread) {
//            System.out.println("released");
            keyboardThread.notifyAll();
            if(error)
                keyboardThread.setError(1);
            else
                keyboardThread.setError(0);
        }

    }

    private void receiveACK(byte[] content) {
        short block_number = conv2bToShort(content);
        System.out.println("ACK " + block_number);
        keyboardThread.setACKBlockNumber(block_number);
        notifyKeyboardThread(false);
    }

    private final int MAX_DATA_SECTION_SIZE = 512;
    private ByteArrayOutputStream dirqStream = new ByteArrayOutputStream();
    private int count_dirq_bytes = 0;
    private void data(byte[] content) {
        /*
        When received a DATA packet - save the data to a file or a buffer depending on if we are in RRQ Command or DIRQ Command and send an ACK packet in return with the corresponding block number written in the DATA packet.
         */
        short packet_size = conv2bToShort(new byte[]{content[0],content[1]});
        boolean is_last_packet = packet_size < MAX_DATA_SECTION_SIZE;
        short block_number = conv2bToShort(new byte[]{content[2],content[3]});
//        System.out.println("Packet size: " + packet_size);
//        System.out.println("Block number: " + block_number);

        byte[] bytes_to_write = Arrays.copyOfRange(content,4,content.length); //excluding OP_CODE at start, remove only packet size and block number

        //RRQ
        if(keyboardThread.getRRQFile() != null) {
            File file = keyboardThread.getRRQFile();

            try(FileOutputStream os = new FileOutputStream(file, true)) {
                os.write(bytes_to_write);
            } catch (IOException e) {
                e.printStackTrace();
            }

            //Once transfer is complete
            if(is_last_packet) {
                System.out.println("RRQ " + file.getName() + " complete");
                keyboardThread.onRRQFinish();
            }


        }

        //DIRQ
        else {
            for(byte b : bytes_to_write) {
                if(b == 0) {
                    String file_name = new String(dirqStream.toByteArray(),0,count_dirq_bytes,StandardCharsets.UTF_8);
                    System.out.println(file_name);
                    //reset buffer
                    dirqStream = new ByteArrayOutputStream();
                    count_dirq_bytes = 0;
                }
                else {
                    dirqStream.write(b);
                    count_dirq_bytes++;
                }
            }
            //TODO: take care of case where there are bytes left in the stream
            if(is_last_packet) {
                synchronized (keyboardThread) {
                    keyboardThread.notifyAll();
                }
            }
        }
        sendACK(block_number);
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

    /**
    Sends a single message
    {@code @throws:} IOException
     */
    private synchronized void send(byte[] bytes) throws IOException {
        out.write(bytes);
        out.flush();
    }

    private void sendACK(short block_number) {
        byte[] ack = new byte[4];
        ack[0] = 0;
        ack[1] = 4;
        ack[2] = convShortTo2b(block_number)[0];
        ack[3] = convShortTo2b(block_number)[1];
        try{
            send(ack);
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    private void bcast(byte[] content) {
        String del_add = (content[0] == 0) ? "del" : "add";
        String file_name = new String(content,1,content.length-2, StandardCharsets.UTF_8);
        System.out.println("BCAST " + del_add + " " + file_name);
    }

    private void error(byte[] content) {
        byte[] errorCode = new byte[]{content[0],content[1]};
        System.out.println("Error " + conv2bToShort(errorCode));
        if(keyboardThread.getRRQFile() != null) {
            keyboardThread.onRRQFinish();
        }
        notifyKeyboardThread(true);
    }

    private short conv2bToShort(byte[] b){
        // converting 2 byte array to a short
        short num = (short) ( (((short) (b[0] & 0xff)) << 8) | (short) (b[1]) & 0x00ff);
        return num;
    }

    private byte[] convShortTo2b(short num){
        // converting short to 2 byte array
        byte[] a_bytes = new byte[2];
        a_bytes[0] = (byte) (num >> 8);
        a_bytes[1] = (byte) (num);
        return a_bytes;
    }

    public boolean shouldTerminate() {
        return false;
    }
}

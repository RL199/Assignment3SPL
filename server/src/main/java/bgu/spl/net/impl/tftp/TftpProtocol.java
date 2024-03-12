package bgu.spl.net.impl.tftp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;

class holder {
    static ConcurrentHashMap<Integer, String> login_ids = new ConcurrentHashMap<>();
}

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

    private boolean shouldTerminate = false;

    private int connectionId;

    private Connections<byte[]> connections;

    /* ------------------------------ Added Fields ------------------------------ */

    private boolean wrqComplete = true;

    private boolean rrqComplete = true;

    private boolean dirqComplete = true;

    private FileOutputStream fos;

    private byte[] dirqContent;

    private byte[] rrqContent;

    private int indexRrq;

    private int indexDirq;

    private File wrqFile;

    private String wrqFileName;

    /* ------------------------------ End of Added Fields ------------------------------ */



    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        //Initiate the protocol with the active connections structure of the server and saves the owner client’s connection id.
        this.connectionId = connectionId;
        this.connections = connections;
        this.shouldTerminate = false;
    }

    /**
     * sendDirqData
     * send the next data packet to the client
     * @param blockNum
     */
    private void sendDirqData(short blockNum){
        byte[] data = new byte[(dirqContent.length-indexDirq >= 512) ? 518 : dirqContent.length-indexDirq + 6];
        data[0] = 0;
        data[1] = 3;
        data[4] = convShortTo2b(blockNum)[0];
        data[5] = convShortTo2b(blockNum)[1];
        short counter = 0;
        for(int i = 6; i < 518 && indexDirq < dirqContent.length; i++){
            data[i] = dirqContent[indexDirq++];
            counter++;
        }
        data[2] = convShortTo2b(counter)[0];
        data[3] = convShortTo2b(counter)[1];
        connections.send(connectionId, data);
        dirqComplete = counter < 512;
    }

    /**
     * sendRrqData
     * send the next data packet to the client
     * @param blockNum
     */
    private void sendRrqData(short blockNum){
        byte[] data = new byte[(rrqContent.length-indexRrq >= 512) ? 518 : rrqContent.length-indexRrq + 6];
        data[0] = 0;
        data[1] = 3;
        data[4] = convShortTo2b(blockNum)[0];
        data[5] = convShortTo2b(blockNum)[1];
        short counter = 0;
        for(int i = 6; i < 518 && indexRrq < rrqContent.length; i++){
            data[i] = rrqContent[indexRrq++];
            counter++;
        }
        data[2] = convShortTo2b(counter)[0];
        data[3] = convShortTo2b(counter)[1];
        connections.send(connectionId, data);
        rrqComplete = counter < 512;
    }


    /**
     * sendBroadcast
     * send broadcast message to all the clients
     * @param fileName
     * @param isAdded
     */
    private void sendBroadcast(byte[] fileName, boolean isAdded){
        try{
            byte[] brc = new byte[3 + fileName.length + 1];
            brc[0] = 0;
            brc[1] = 9;
            brc[2] = (byte)(isAdded ? 1 : 0);
            for(int i = 0; i < fileName.length; i++){
                brc[i+3] = fileName[i];
            }
            brc[brc.length-1] = 0;
            System.out.println(Arrays.toString(brc));
            connections.broadcast(brc);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * sendAck
     * send ack message to the client
     * @param blockNum
     */
    private void sendAck(short blockNum){
        byte[] ack = new byte[4];
        ack[0] = 0;
        ack[1] = 4;
        ack[2] = convShortTo2b(blockNum)[0];
        ack[3] = convShortTo2b(blockNum)[1];
        try{
            connections.send(connectionId, ack);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * sendError
     * send error message to the client
     * @param errornNum
     * @param errorMsg
     */
    private void sendError(byte errornNum, String errorMsg){

        byte[] error = new byte[4 + errorMsg.getBytes().length + 1];
        error[0] = 0;
        error[1] = 5;
        error[2] = 0;
        error[3] = errornNum;
        for(int i = 0; i < errorMsg.getBytes().length; i++){
            error[i+4] = errorMsg.getBytes()[i];
        }
        error[error.length-1] = 0;
        try{
            connections.send(connectionId, error);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private void rrq(byte[] message){
        //read request
        if(!holder.login_ids.containsKey(connectionId)){
            //send error 6
            sendError((byte)6, "");
            return;
        }
        String fileName = "";
        try{
            //get the file name using UTF-8
            fileName = new String(message, 2, message.length-3, StandardCharsets.UTF_8);
        }catch(Exception e){
            e.printStackTrace();
        }
        //check if the file exists in Files directory
        //TODO: Perhaps change to server/Files
        File file = new File("Files/" + fileName);
        if(file.exists()){
            //send the file to the client
            try{
                FileInputStream fis = new FileInputStream(file);
                rrqContent = new byte[(int)file.length()];
                //TODO: synchronize?
                fis.read(rrqContent);
                fis.close();
                indexRrq = 0;
                sendRrqData((short)1);
            }catch(IOException e){
                e.printStackTrace();
            }
        }
        else{
            //send error 1
            sendError((byte)1, "");
        }
    }

    private void ack(byte[] message){
        //ack
        if(!holder.login_ids.containsKey(connectionId)){
            //send error 6
            sendError((byte)6, "");
            return;
        }
        //send the next data packet if the file is not complete
        short blockNum = conv2bToShort(new byte[]{message[2], message[3]});
        System.out.println("ACK " + blockNum);
        if(blockNum > 0 && !rrqComplete){
            blockNum++;
            sendRrqData(blockNum);
        }
        else if(blockNum > 0 && !dirqComplete){
            blockNum++;
            sendDirqData(blockNum);
        }
    }

    private void wrq(byte[] message){
        //write request
        if(!holder.login_ids.containsKey(connectionId)){
            //send error 6
            sendError((byte)6, "");
            return;
        }
        //check if the file already exists in Files directory
        wrqFileName = "";
        try{
            //get the file name using UTF-8
            wrqFileName = new String(message, 2, message.length-3, StandardCharsets.UTF_8);
        }catch(Exception e){
            e.printStackTrace();
        }

        //TODO: perhaps change to server/Files
        wrqFile = new File("Files/" + wrqFileName);
        if(wrqFile.exists()){
            //send error 5
            sendError((byte)5, "");
            return;
        }
        try{
            //create the file
            fos = new FileOutputStream(wrqFileName);
            wrqComplete = false;
            //send ack 0
            sendAck((short)0);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

//TODO: check where synchronized is needed.
    private void data(byte[] message){
        //data
        if(!holder.login_ids.containsKey(connectionId)){
            //send error 6
            sendError((byte)6, "");
            return;
        }
        try{
            //TODO: synchronize?
            fos.write(message, 6, message.length-6);
            wrqComplete = message.length < 518;
            //send ack with the block number
            short blockNum = conv2bToShort(new byte[]{message[4], message[5]});
            sendAck(blockNum);
        }catch(Exception e){
            e.printStackTrace();
        }
        if(wrqComplete){
            //add the file to the Files directory
            try{
                fos.close();
                if(wrqFile.exists()){
                    //send error 5
                    sendError((byte)5, "");
                    return;
                }
                //move the file to the Files directory
                Path source = Paths.get(wrqFileName);
                //TODO: perhaps change to server/Files
                Path target = Paths.get("Files/" + wrqFileName);
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }catch(Exception e){
                e.printStackTrace();
            }
            // send broadcast that the file was added
            sendBroadcast(wrqFileName.getBytes(), true);
        }
    }


    private void error(byte[] message){
        if(!holder.login_ids.containsKey(connectionId)){
            //send error 6
            sendError((byte)6, "");
            return;
        }
        //error
        byte[] errorCode = {message[2], message[3]};
        //check the error code using switch case
        short code = conv2bToShort(errorCode);
        String errorMsg = "";
        try{
            errorMsg = new String(message, 4, message.length-5, StandardCharsets.UTF_8);
        }catch(Exception e){
            e.printStackTrace();
        }
        System.out.println("Error " + code + " " + errorMsg);
    }

    private void dirq(byte[] message){
        //directory listing request
        if(!holder.login_ids.containsKey(connectionId)){
            //send error 6
            sendError((byte)6, "");
            return;
        }
        //TODO: perhaps change to server/Files
        File folder = new File("Files/");
        File[] listOfFiles = folder.listFiles();
        //send the names of the files divided by 0 in the directory in DATA packets
        ArrayList<byte[]> fileNamesarrays = new ArrayList<>();
        int lengthCounter = 0;
        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) {
                byte[] fileName = listOfFiles[i].getName().getBytes();
                fileNamesarrays.add(fileName);
                lengthCounter += fileName.length + 1;
            }
        }
        //join all the file names data
        dirqContent = new byte[lengthCounter];
        int index = 0;
        for(int i = 0; i < fileNamesarrays.size() && index < dirqContent.length; i++){
            for(int j = 0; j < fileNamesarrays.get(i).length; j++){
                dirqContent[index++] = fileNamesarrays.get(i)[j];
                if(j==fileNamesarrays.get(i).length-1){
                    dirqContent[index++] = 0;
                }
            }
        }
        //send the file names data
        indexDirq = 0;
        sendDirqData((short)1);
    }

    private void logrq(byte[] message){
        //login request
        //check if the user is already logged in
        System.out.println("message: " + Arrays.toString(message));
        if(holder.login_ids.containsKey(connectionId)){
            //send error 7
            sendError((byte)7, "");
            return;
        }
        String userName = "";
        try{
            //get the user name using UTF-8
            userName = new String(message, 2, message.length-3, StandardCharsets.UTF_8);
        }catch(Exception e){
            e.printStackTrace();
        }
        //check if the user name is already logged in
        if(holder.login_ids.containsValue(userName)){
            //send error 7
            sendError((byte)7, "");
            return;
        }
        holder.login_ids.put(connectionId, userName);
        //send ack 0
        sendAck((short)0);
    }

//TODO:if more than one error applies, select the lower error code.
    private void delrq(byte[] message){
        //delete request
        if(!holder.login_ids.containsKey(connectionId)){
            //send error 6
            sendError((byte)6, "");
            return;
        }
        String fileName = "";
        try{
            //get the file name using UTF-8
            fileName = new String(message, 2, message.length-3, StandardCharsets.UTF_8);
            System.out.println("message: " + Arrays.toString(message));
            System.out.println("Filename: " + fileName);
        }catch(Exception e){
            //error
        }
        //check if the file exists in Files directory
        File file = new File("Files/" + fileName);
        if(file.exists()){
            file.delete();
            //send ack 0
            sendAck((short)0);
            //broadcast file deleted
            sendBroadcast(fileName.getBytes(), false);
        }
        else{
            //send error 1
            sendError((byte)1, "");
        }
    }

    private void bcst(byte[] message){
        //broadcast file deleted or added. the server not suppose to get this opcode
        //send error 4
        sendError((byte)4, "");
    }

    private void disc(byte[] message){
        //logout request
        if(holder.login_ids.containsKey(connectionId)){
            holder.login_ids.remove(connectionId);
            //send ack 0
            sendAck((short)0);
            this.connections.disconnect(connectionId);
            shouldTerminate = true;
        }
        else{
            //send error 6
            sendError((byte)6, "");
        }
    }

    @Override
    public void process(byte[] message) {
        //As in MessagingProtocol, processes a given message. Unlike MessagingProtocol,
        // responses are sent via the connections object send functions (if needed)
        if(!shouldTerminate()){
            byte[] opCode = {message[0], message[1]};
            switch(conv2bToShort(opCode)){
                case 1:
                    //read request
                    rrq(message);
                    break;
                case 2:
                    //write request
                    wrq(message);
                    break;
                case 3:
                    //data
                    data(message);
                    break;
                case 4:
                    //ack
                    ack(message);
                    break;
                case 5:
                    //error
                    error(message);
                    break;
                case 6:
                    //directory listing request
                    dirq(message);
                    break;
                case 7:
                    //login request
                    logrq(message);
                    break;
                case 8:
                    //delete request
                    delrq(message);
                    break;
                case 9:
                    //broadcast file deleted or added
                    bcst(message);
                    break;
                case 10:
                    //logout request
                    disc(message);
                    break;
                default:
                    //invalid, send error 4
                    sendError((byte)4, "");
                    break;
            }
        }
    }

    @Override
    public boolean shouldTerminate() {
        //true if the connection should be terminated
        //TODO: check where shouldTerminate is needed to be called
        if(shouldTerminate){
            holder.login_ids.remove(connectionId);
            this.connections.disconnect(connectionId);
        }
        return shouldTerminate;
    }

    private byte[] convShortTo2b(short num){
        // converting short to 2 byte array
        byte[] a_bytes = new byte[2];
        a_bytes[0] = (byte) (num >> 8);
        a_bytes[1] = (byte) (num);
        return a_bytes;
    }

    private short conv2bToShort(byte[] b){
    // converting 2 byte array to a short
        short num = (short) ( (((short) (b[0] & 0xff)) << 8) | (short) (b[1]) & 0x00ff);
        return num;
    }
}

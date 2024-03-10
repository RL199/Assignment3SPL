package bgu.spl.net.impl.tftp;

import java.util.ArrayList;
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

class holder{
    static ConcurrentHashMap<Integer, String> login_ids = new ConcurrentHashMap<>();
}

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

    private boolean shouldTerminate = false;

    private int connectionId;

    private Connections<byte[]> connections;

    /* ------------------------------ Added Fields ------------------------------ */

    private boolean isFileComplete = true;

    FileOutputStream fos;



    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        //Initiate the protocol with the active connections structure of the server and saves the owner client’s connection id.
        this.connectionId = connectionId;
        this.connections = connections;
        this.shouldTerminate = false;
    }

    private void rrq(byte[] message){
        //read request
        if(!holder.login_ids.containsKey(connectionId)){
            //send error 6
            try{
                byte[] error = {0, 5, 0, 6, 0};
                connections.send(connectionId, error);
            }catch(Exception e){
                e.printStackTrace();
            }
            return;
        }
        String fileName = "";
        try{
            //get the file name using UTF-8
            fileName = new String(message, 2, message.length-3, StandardCharsets.UTF_8);
        }catch(Exception e){
            //error
        }
        //check if the file exists in Files directory
        File file = new File("Files/" + fileName);
        if(file.exists()){
            //send the file to the client
            try{
                FileInputStream fileInputStream = new FileInputStream(file);
                byte[] fileContent = new byte[(int)file.length()];
                fileInputStream.read(fileContent);
                fileInputStream.close();
                int blockNum = 1;
                int index = 0;
                while(index < fileContent.length){
                    byte[] data = new byte[516];
                    data[0] = 0;
                    data[1] = 3;
                    data[2] = convShortTo2b((short)blockNum)[0];
                    data[3] = convShortTo2b((short)blockNum)[1];
                    for(int i = 4; i < 516; i++){
                        data[i] = fileContent[index++];
                    }
                    connections.send(connectionId, data);
                    blockNum++;
                    //wait for ack
                    synchronized(this){
                        try{
                            this.wait();
                        }catch(Exception e){
                            e.printStackTrace();
                        }
                    }
                }
            }catch(IOException e){
                e.printStackTrace();
            }
        }
        else{
            //send error 1
            try{
                byte[] error = {0, 5, 0, 1, 0};
                connections.send(connectionId, error);
            }catch(Exception e){
                e.printStackTrace();
            }
        }


    }

    private void wrq(byte[] message){
        //write request
        if(!holder.login_ids.containsKey(connectionId)){
            //send error 6
            try{
                byte[] error = {0, 5, 0, 6, 0};
                connections.send(connectionId, error);
            }catch(Exception e){
                e.printStackTrace();
            }
            return;
        }
        //check if the file already exists in Files directory
        String fileName = "";
        try{
            //get the file name using UTF-8
            fileName = new String(message, 2, message.length-3, StandardCharsets.UTF_8);
        }catch(Exception e){
            e.printStackTrace();
        }
        File file = new File("Files/" + fileName);
        if(file.exists()){
            //send error 5
            try{
                byte[] error = {0, 5, 0, 5, 0};
                connections.send(connectionId, error);
            }catch(Exception e){
                e.printStackTrace();
            }
            return;
        }
        //send ack
        short blockNum = 0;
        isFileComplete = false;
        try{
            fos = new FileOutputStream(fileName);
            while(!isFileComplete){
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
                //wait for data
                synchronized(this){
                    try{
                        this.wait();
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
                blockNum++;
            }
            fos.close();
        }catch(Exception e){
            e.printStackTrace();
        }
        //add the file to the Files directory
        try{
            Path source = Paths.get(fileName);
            Path target = Paths.get("Files/" + fileName);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception e){
            e.printStackTrace();
        }
        //send broadcast that the file was added
        byte[] bcst = new byte[3 + fileName.getBytes().length + 1];
        bcst[0] = 0;
        bcst[1] = 9;
        bcst[2] = 1;
        for(int i = 0; i < fileName.getBytes().length; i++){
            bcst[i+3] = fileName.getBytes()[i];
        }
        bcst[bcst.length-1] = 0;
        try{
            connections.broadcast(bcst);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private void data(byte[] message){
        //data
        if(!holder.login_ids.containsKey(connectionId)){
            //send error 6
            try{
                byte[] error = {0, 5, 0, 6, 0};
                connections.send(connectionId, error);
            }catch(Exception e){
                e.printStackTrace();
            }
            return;
        }
        try{
            fos.write(message, 4, message.length-4);
        }catch(Exception e){
            e.printStackTrace();
        }
        isFileComplete = message.length < 516;
        synchronized(this){
            this.notify();
        }
    }

    private void ack(byte[] message){
        //ack
        if(!holder.login_ids.containsKey(connectionId)){
            //send error 6
            try{
                byte[] error = {0, 5, 0, 6, 0};
                connections.send(connectionId, error);
            }catch(Exception e){
                e.printStackTrace();
            }
            return;
        }
        byte[] blockNum = {message[2], message[3]};
        System.out.println("ACK " + conv2bToShort(blockNum));
        if(conv2bToShort(blockNum) != 0){
            synchronized(this){
                this.notify();
            }
        }
    }

    private void error(byte[] message){
        if(!holder.login_ids.containsKey(connectionId)){
            //send error 6
            try{
                byte[] error = {0, 5, 0, 6, 0};
                connections.send(connectionId, error);
            }catch(Exception e){
                e.printStackTrace();
            }
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
            try{
                byte[] error = {0, 5, 0, 6, 0};
                connections.send(connectionId, error);
            }catch(Exception e){
                e.printStackTrace();
            }
            return;
        }
        File folder = new File("Files");
        File[] listOfFiles = folder.listFiles();
        //send the names of the files divided by 0 in the directory in DATA packets
        ArrayList<byte[]> fileNamesarrays = new ArrayList<>();
        int lengthCounter = 0;
        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) {
                byte[] fileName = listOfFiles[i].getName().getBytes();
                fileNamesarrays.add(fileName);
                lengthCounter += fileName.length;
            }
        }
        //join all the file names data
        byte[] allFileNames = new byte[lengthCounter];
        int index = 0;
        for(int i = 0; i < fileNamesarrays.size(); i++){
            for(int j = 0; j < fileNamesarrays.get(i).length; j++){
                allFileNames[index++] = fileNamesarrays.get(i)[j];
                if(j==fileNamesarrays.get(i).length-1){
                    allFileNames[index++] = 0;
                }
            }
        }
        //send the file names data in 512 bytes packets
        int blockNum = 1;
        index = 0;
        while(index < allFileNames.length){
            byte[] data = new byte[516];
            data[0] = 0;
            data[1] = 3;
            data[2] = convShortTo2b((short)blockNum)[0];
            data[3] = convShortTo2b((short)blockNum)[1];
            for(int i = 4; i < 516; i++){
                data[i] = allFileNames[index++];
            }
            connections.send(connectionId, data);
            blockNum++;
            //wait for ack
            synchronized(this){
                try{
                    this.wait();
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    private void logrq(byte[] message){
        //login request
        //check if the user is already logged in
        if(holder.login_ids.containsKey(connectionId)){
            //send error 7
            try{
                byte[] error = {0, 5, 0, 7, 0};
                connections.send(connectionId, error);
            }catch(Exception e){
                e.printStackTrace();
            }
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
            try{
                byte[] error = {0, 5, 0, 7, 0};
                connections.send(connectionId, error);
            }catch(Exception e){
                e.printStackTrace();
            }
            return;
        }
        holder.login_ids.put(connectionId, userName);
    }
//TODO:if more than one error applies, select the lower error code.
    private void delrq(byte[] message){
        //delete request
        if(!holder.login_ids.containsKey(connectionId)){
            //send error 6
            try{
                byte[] error = {0, 5, 0, 6, 0};
                connections.send(connectionId, error);
            }catch(Exception e){
                e.printStackTrace();
            }
            return;
        }
        String fileName = "";
        try{
            //get the file name using UTF-8
            fileName = new String(message, 2, message.length-3, StandardCharsets.UTF_8);
        }catch(Exception e){
            //error
        }
        //check if the file exists in Files directory
        File file = new File("Files/" + fileName);
        if(file.exists()){
            file.delete();
            //broadcast file deleted
            //add the file name bytes to the broadcast message and 0 byte
            byte[] fileNameBytes = fileName.getBytes();
            byte[] bcst = new byte[3 + fileNameBytes.length + 1];
            bcst[0] = 0;
            bcst[1] = 9;
            bcst[2] = 0;
            for(int i = 0; i < fileNameBytes.length; i++){
                bcst[i+3] = fileNameBytes[i];
            }
            bcst[bcst.length-1] = 0;
            try{
                connections.broadcast(bcst);
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        else{
            //send error 1
            try{
                byte[] error = {0, 5, 0, 1, 0};
                connections.send(connectionId, error);
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    private void bcst(byte[] message){
        //broadcast file deleted or added. the server not suppose to get this opcode
        if(!holder.login_ids.containsKey(connectionId)){
            //send error 6
            try{
                byte[] error = {0, 5, 0, 6, 0};
                connections.send(connectionId, error);
            }catch(Exception e){
                e.printStackTrace();
            }
            return;
        }
        //send error 4
        try{
            byte[] error = {0, 5, 0, 4, 0};
            connections.send(connectionId, error);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private void disc(byte[] message){
        //logout request
        //TODO: check how to wait for file transfer to end
        if(holder.login_ids.containsKey(connectionId)){
            holder.login_ids.remove(connectionId);
            this.connections.disconnect(connectionId);
            shouldTerminate = true;
        }
        else{
            //send error 6
            try{
                byte[] error = {0, 5, 0, 6, 0};
                connections.send(connectionId, error);
            }catch(Exception e){
                e.printStackTrace();
            }
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
                    try{
                        byte[] error = {0, 5, 0, 4, 0};
                        connections.send(connectionId, error);
                    }catch(Exception e){
                        e.printStackTrace();
                    }
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
        short num = (short)(((short)(b[0] & 0xFF)) | ((short)(b[1] & 0x0FF)));
        return num;
    }
}

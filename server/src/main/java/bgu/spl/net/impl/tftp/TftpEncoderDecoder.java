package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import java.util.Arrays;


public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    //TODO: Implement here the TFTP encoder and decoder

    private byte[] bytes = new byte[1 << 10];
    private int len = 0;
    private int op = -1;

    private Short packet_size = null; //in use only in case of operation 3 (DATA)

    @Override
    public byte[] decodeNextByte(byte nextByte) {

        //If array is too small
        if(len+1 == bytes.length)
            bytes = Arrays.copyOf(bytes,2 * len);


        bytes[len++] = nextByte;

        if(len < 2) return null;

        if(len == 2) {//2 bytes entered so far
            op = (int) byte_array_to_short(bytes);

        }
        switch (op) {
            case 1: //RRQ
            case 2: //WRQ
            case 5: //ERROR
            case 7: //LOGRQ
            case 8: //DELRQ
            case 9: //BCAST
                if(nextByte == 0)  //End of command
                    return msg();
                break;
            case 3: //DATA
                //handled later
                break;
            case 4: //ACK
                if(len == 4)
                    return msg();
                break;
            case 6: //DIRQ
            case 10: //DISC
                return msg();
            default: //NOT DEFINED COMMAND
                System.out.println("Not Defined Command: " + op);
                break;

        }

        //DATA
        if(op == 3) {
            if(len == 4)
                //need to read 4 first bytes in order to know how many bytes to read in total
                packet_size = byte_array_to_short(new byte[]{bytes[2],bytes[3]});
            if(len > 4) {
                if(len == 6 + packet_size)
                    return msg();
            }
        }

        return null;
    }

    @Override
    public byte[] encode(byte[] message) {
        return message;
    }

    //Added private functions, intended for implementation
    private byte[] short_to_byte_array(short a) {
        return new byte []{(byte) (a >> 8), (byte)(a & 0xff) };
    }

    private short byte_array_to_short(byte[] b) {
        return (short) ( (((short) (b[0] & 0xff)) << 8) | (short) (b[1]) & 0x00ff);
//        return (short) (((short) bytes [0]) << 8 | ( short ) (bytes [1]));
    }

    private byte[] msg() {
        byte[] result = Arrays.copyOf(bytes, len);
        bytes = new byte[1 << 10];
        len = 0;
        op = -1;
        return result;
    }
}

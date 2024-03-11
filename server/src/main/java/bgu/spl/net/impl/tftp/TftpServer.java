package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.srv.BaseServer;
import bgu.spl.net.srv.BlockingConnectionHandler;
import java.util.function.Supplier;
import bgu.spl.net.srv.Connections;

public class TftpServer extends BaseServer<byte[]> {

    private Connections<byte[]> connections;

    int connectionIdCounter = 0;

    public TftpServer(int port, Supplier<BidiMessagingProtocol<byte[]>> protocolFactory, Supplier<MessageEncoderDecoder<byte[]>> encdecFactory) {
        super(port, protocolFactory, encdecFactory);
        connections = new ConnectionsImpl<>();
    }

    @Override
    protected void execute(BlockingConnectionHandler<byte[]> handler) {
        handler.start(connectionIdCounter++ ,connections);
        new Thread(handler).start();

    }

    public static void main(String[] args) {
        TftpServer server = new TftpServer(
                7777,
                TftpProtocol::new,
                TftpEncoderDecoder::new
        );
        server.serve();
    }
}

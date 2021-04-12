package services.control;

import domain.control.CommandMonitor;
import domain.control.ControlCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;

public class ControlPad {
    private static final Logger LOG = LoggerFactory.getLogger(ControlPad.class);
    private CommandMonitor commandMonitor;
    private AsynchronousServerSocketChannel serverSocketChannel;

    public ControlPad(CommandMonitor commandMonitor, String bindAddr, int bindPort ) throws IOException {
        this.commandMonitor = commandMonitor;

        LOG.info("Starting command server on {}:{}", bindAddr, bindPort);

        InetSocketAddress sockAddr = new InetSocketAddress(bindAddr, bindPort);

        //create a socket channel and bind to local bind address
        serverSocketChannel =  AsynchronousServerSocketChannel.open().bind(sockAddr);

        //start to accept the connection from client
        serverSocketChannel.accept(serverSocketChannel, new CompletionHandler<>() {
            @Override
            public void completed(AsynchronousSocketChannel sockChannel, AsynchronousServerSocketChannel serverSock) {
                //a connection is accepted, start to accept next connection
                serverSock.accept(serverSock, this);
                //start to read message from the client
                startRead(sockChannel);
            }

            @Override
            public void failed(Throwable exc, AsynchronousServerSocketChannel serverSock) {
                LOG.error("fail to accept a connection");
            }
        } );
    }

    public void shutdown() throws IOException {
        try {
            LOG.info("Closing server socket channel.");
            serverSocketChannel.close();
        } catch (IOException e) {
            //Ignore
        }
    }

    private void startRead( AsynchronousSocketChannel sockChannel ) {
        final ByteBuffer buf = ByteBuffer.allocate(2048);

        //read message from client
        sockChannel.read( buf, sockChannel, new CompletionHandler<>() {
            @Override
            public void completed(Integer result, AsynchronousSocketChannel channel) {
                if (result == -1) {
                    try {
                        LOG.info("Closing socket channel.");
                        sockChannel.close();
                        return;
                    } catch (IOException e) {
                        //Ignore
                    }
                }

                buf.flip();
                String s = StandardCharsets.UTF_8.decode(buf).toString().strip();
                LOG.info("Received {}", s);
                buf.clear();

                ControlCommand.Command command = ControlCommand.getCommandByKey(s);
                if (command == null) {
                    buf.put("Invalid command input.\n".getBytes(StandardCharsets.UTF_8));
                } else {
                    buf.put(command.getMessage().getBytes(StandardCharsets.UTF_8));
                    commandMonitor.submitCommand(command);
                }

                startWrite(channel, buf);
                //start to read next message again
                startRead( channel );
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                LOG.info("fail to read message from client");
            }
        });
    }

    private void startWrite( AsynchronousSocketChannel sockChannel, final ByteBuffer buf) {
        sockChannel.write(buf, sockChannel, new CompletionHandler<>() {

            @Override
            public void completed(Integer result, AsynchronousSocketChannel channel) {
                //finish to write message to client, nothing to do
                channel.write(buf.flip());
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                //fail to write message to client
                LOG.info("Fail to write message to client");
            }
        });
    }
}

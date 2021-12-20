import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConverterServer {
	
	Logger logger;

	public ConverterServer(int port, Logger logger) throws IOException {
		this.logger = logger;

		ServerSocket serverSocket = new ServerSocket(port);
        logger.log(Level.INFO, "Listening to port "+port);
        Executor service = Executors.newCachedThreadPool();
        Socket socket = null;
        while (true) {
            try {
                socket = serverSocket.accept();
                SocketAddress remoteAddress = socket.getRemoteSocketAddress();		
                logger.log(Level.INFO, "Accepted connection from "+remoteAddress);
                service.execute(new ConverterService(socket, logger));	
            } catch (Exception e) {
                // some other error occurred. Make sure the socket has been closed
                if (!socket.isClosed()) {
                    socket.close(); 
                }
                if(!serverSocket.isClosed()) {
                	serverSocket.close();
                }
            }
        }
        
    }	
	

	public static void main(String[] args) {
		Logger logger = Logger.getLogger("LAB4 Server");
		 
        int port = 2001;
        try {
            new ConverterServer(port, logger);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "ERROR: Could not start the server", e);;
            e.printStackTrace();
        } 	 
	} 
    
    //fine classe ConverterServer
}

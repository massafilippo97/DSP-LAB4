package it.polito.dsp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets; 
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;



public class ConverterClient {

	public ConverterClient(InetAddress serverAddress, int port, String oldFormat, String newFormat, String filename) throws UnknownHostException, IOException {
		
		Socket socket = new Socket(serverAddress, port);

		socket.setSoTimeout(5000);
		System.out.println("LOG: Connected to server "+serverAddress.toString().substring(1)+":"+port);

        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        try {
            InputStream is = socket.getInputStream();
            FileInputStream fin = new FileInputStream("image/"+filename);//utile a bufferare l'immagine e mandarla al server
            File file = new File("image/"+filename); //utile solo per ottenere la lunghezza della immagine
            
            byte [] fileByteArray  = new byte[1024];
            int fileSizeToSend = (int) file.length();
           
            // converti i diversi formati in stringhe di bytes e mandale al server [|jpg|png|827364|xxxxxxx....|]
            byte[] oldFormatByteEncoded = oldFormat.getBytes(StandardCharsets.US_ASCII); 
            byte[] newFormatByteEncoded = newFormat.getBytes(StandardCharsets.US_ASCII);
            out.write(oldFormatByteEncoded);
            out.write(newFormatByteEncoded);
            out.writeInt(fileSizeToSend); //manda anche la lunghezza del file in termini di bytes

          
            // manda l'immagine al server
            int count=0;
            while ((count = fin.read(fileByteArray)) > 0)
                 out.write(fileByteArray, 0, count);
            out.flush(); //Flushes this data output stream. This forces any buffered output bytes to be written out to the stream.
            fin.close();
            System.out.println("LOG: The file has been sent.");

            
            // incomincia a leggere ciò che il server ritorna (feedback char)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            //FileOutputStream outputStream;
            int fileSizeToRead=0;
 
            char feedbackChar  = (char)is.read(); // in.readChar();
            System.out.println("LOG: The response code has been received.");
            
            switch(feedbackChar) {
                case '0':
                    fileSizeToRead = in.readInt();
                    int bytesToRead = fileSizeToRead;
                    
                    //read file chunks, until less than 1024 bytes remain
                    while(fileSizeToRead > 1024) {
                        // System.out.println(fileSizeToRead);
                        int readBytes = in.read(fileByteArray, 0, 1024);
                        baos.write(fileByteArray, 0, readBytes);
                        bytesToRead -= readBytes;
                        fileSizeToRead=bytesToRead;
                        fileByteArray = new byte[1024]; //ricrea nuovo buffer temporaneo
                    }
                    //read last chunk
                    while(bytesToRead > 0) {
                        int readBytes = in.read(fileByteArray, 0, bytesToRead);
                        baos.write(fileByteArray, 0, readBytes);
                        bytesToRead -= readBytes;
                        fileByteArray = new byte[1024]; //ricrea nuovo buffer temporaneo
                    }			 
                    
        			try(OutputStream outputStream = new FileOutputStream("image/output."+newFormat.toLowerCase())) {
        				baos.writeTo(outputStream);
        				outputStream.close();
        			}
                    
                    System.out.println("LOG: The converted file has been received.");
                    break;
                case '1':
                    System.out.println("ERROR: Wrong Request");
                    break;
                case '2':
                    System.out.println("ERROR: Internal Server Error");
                    break;
                default:
                    System.out.println("ERROR: Unexpected feedback character value");
                    break;
            }
		    socket.close();
        }
        catch (IOException e) { 
            System.out.println("LOG: ERROR! closing the application...\n");
            e.printStackTrace();
        } 
	}

	public static void main(String[] args) {
		if (args.length!=3) {
			System.err.println("Check command line arguments: input, output, filename");
			System.exit(1);
		}
		if ( !args[0].matches("[A-Z]{3}") || !args[1].matches("[A-Z]{3}") ) {
			System.err.println("Input and output must be 3 uppercase letters each.");
			System.exit(1);
		}
		try {  
            String oldFormat = args[0]; 
            String newFormat = args[1];
            String filename = args[2];
			System.out.println("Input: "+oldFormat+" Output: "+newFormat+" Filename: "+filename);
	        //System.out.println("\r\nConnected to Server: " + InetAddress.getByName("0.0.0.0").toString());
            new ConverterClient(InetAddress.getByName("0.0.0.0"),2001, oldFormat,newFormat,filename);
		} catch (IOException e) {
			System.err.println("I/O Exception");
			e.printStackTrace();
		}
	}

}

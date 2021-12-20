import java.io.IOException; 
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream; 
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;


public class ConverterService implements Runnable {
	 
	private static final int CHUNK_LENGTH = 1024;
	private static final int TIMEOUT = 5*1000;

	Socket socket;
	Logger logger;
	DataInputStream inputSocketStream = null;
    DataOutputStream outputSocketStream = null;
    boolean successFlag = true;
    int responseCode = 0;
    String errorMessage = null;
    String oldFormat = null;
    String newFormat = null;
    int fileLength;

	public ConverterService(Socket socket, Logger logger) {
		this.socket = socket;
		this.logger = logger;
		try {
			this.socket.setSoTimeout(TIMEOUT);
			inputSocketStream = new DataInputStream(socket.getInputStream());
			outputSocketStream = new DataOutputStream(socket.getOutputStream());
		} catch (SocketException e) {
			errorMessage = new String("Error in setting the timeout for the socket.");
			successFlag = false;
			responseCode = 2; //Internal Server Error
			e.printStackTrace();
		} catch (IOException e) {
			errorMessage = new String("Error in accessing the socket I/O streams.");
			successFlag = false;
			responseCode = 2; //Internal Server Error
			e.printStackTrace();
		}
	}

//	@Override
	public void run() {
		try { 
			service();
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Could not complete service", e);
		} finally {
			if (socket!=null && !socket.isClosed())
				try {
					socket.close();
				} catch (IOException e) {
					logger.log(Level.SEVERE, "Could not close socket", e);;
				}
		}
	}
	
	public void service() throws IOException {
        //accetta in input gli args, apri l'immagine e memorizzala in un buffer
        
		if (successFlag){ // read the metadata (old and new image format) from the client
			try {
				//read the original type of the file
				byte[] typeArray = new byte[3];
				int totalReadBytes = 0;
				while(totalReadBytes < 3) {
					int readBytes = inputSocketStream.read(typeArray, totalReadBytes, 3-totalReadBytes);
					totalReadBytes += readBytes;
				}
				oldFormat = new String(typeArray, StandardCharsets.US_ASCII);
				
				//read the target type of the file
				typeArray = new byte[3];
				totalReadBytes = 0;
				while(totalReadBytes < 3) {
					int readBytes = inputSocketStream.read(typeArray, totalReadBytes, 3-totalReadBytes);
					totalReadBytes += readBytes;
				}
				newFormat = new String(typeArray, StandardCharsets.US_ASCII);
				
				System.out.println("LOG: The information about the media types has been received.");
				
				//check that the [old and new] media types are supported by the conversion service
				if((!oldFormat.equalsIgnoreCase("png") && !oldFormat.equalsIgnoreCase("jpg")
					&& !oldFormat.equalsIgnoreCase("gif")) || (!newFormat.equalsIgnoreCase("png") 
					&& !newFormat.toString().equalsIgnoreCase("jpg") && !newFormat.equalsIgnoreCase("gif") )) 
				{
					successFlag = false;
					errorMessage = new String("Media types not supported.");
					responseCode = 1;
				}

			} catch (SocketException e) {
				successFlag = false;
				errorMessage = new String("Timeout expired for reading the medatadata.");
				responseCode = 1;
				e.printStackTrace();
			}  catch (IOException e) {
				successFlag = false;
				errorMessage = new String("Error in receiving the metadata.");
				responseCode = 2;
				e.printStackTrace();
			}
		}
		
		//read the file length value sent by the client
		if(successFlag) {
			try {
				fileLength = inputSocketStream.readInt();
				System.out.println("LOG: The information about the file length has been received.");
			} catch (SocketException e) {
				successFlag = false;
				errorMessage = new String("Timeout expired for receiving the file length.");
				responseCode = 1;
				e.printStackTrace();
			}  catch (IOException e) {
				successFlag = false;
				errorMessage = new String("Error in receiving the file length.");
				responseCode = 2;
				e.printStackTrace();
			}
		}
		
		//start the reading process of the file sent by the client
		ByteArrayOutputStream baos = new ByteArrayOutputStream(); //buffer dove memorizzare l'immagine ricevuta
		if(successFlag) {
				try {
					byte[] fileArray = new byte[CHUNK_LENGTH];
					int bytesToRead = fileLength;
					
					//read file chunks, until less than CHUNK_LENGTH bytes remain
					while(fileLength > CHUNK_LENGTH) {
						int readBytes = inputSocketStream.read(fileArray, 0, CHUNK_LENGTH);
						baos.write(fileArray, 0, readBytes);
						bytesToRead -= readBytes;
						fileLength=bytesToRead;
						fileArray = new byte[CHUNK_LENGTH];
					}
					//read last chunk
					while(bytesToRead > 0) {
						int readBytes = inputSocketStream.read(fileArray, 0, bytesToRead);
						baos.write(fileArray, 0, readBytes);
						bytesToRead -= readBytes;
						fileArray = new byte[CHUNK_LENGTH];
					}
					
					System.out.println("LOG: The file has been received.");
					
				} catch (SocketException e) {
					successFlag = false;
					errorMessage = new String("Timeout expired for receiving the file.");
					responseCode = 1;
					e.printStackTrace();
				}	catch (IOException e) {
					successFlag = false;
					errorMessage = new String("Error in receiving the file.");
					responseCode = 2;
					e.printStackTrace();
				}
				
		}
		
		//start the conversion process of the file
		ByteArrayOutputStream baosImageToSend = new ByteArrayOutputStream(); //buffer dove memorizzare l'immagine convertita
		if(successFlag) {
			try {
				byte[] bytes = baos.toByteArray(); //baos == ByteArrayOutputStream baos di riga 136
			    ByteArrayInputStream bais = new ByteArrayInputStream(bytes); //baos (immagine ricevuta) --> bytearray --> byteArrayInputStream
				BufferedImage imageReceived;
				imageReceived = ImageIO.read(bais); 
				ImageIO.write(imageReceived, newFormat.toString().toLowerCase(), baosImageToSend); //converti nel nuovo formato e memorizzalo all'interno del buffer baosImageToSend
				System.out.println("LOG: The file has been converted.");
			} catch (IOException e) {
				successFlag = false;
				errorMessage = new String("Error during the image conversion.");
				responseCode = 2;
				e.printStackTrace();
			}
		}
		
		//send back the response to the client (char, lenght value, bytes)
		//case 0: successFlag
		if(successFlag) {
			try {
				//send the successFlag code ('0')
				outputSocketStream.write('0');
				
				//send the length of the converted file
				int convertedLength = baosImageToSend.size();
				outputSocketStream.writeInt(convertedLength);
				
				//send the converted file
				BufferedInputStream bisImageToSend = new BufferedInputStream(new ByteArrayInputStream(baosImageToSend.toByteArray()));
				byte[] buffer = new byte[CHUNK_LENGTH];
	            int bytesToWrite;
				while ((bytesToWrite = bisImageToSend.read(buffer, 0, CHUNK_LENGTH)) != -1) {
					  outputSocketStream.write(buffer, 0, bytesToWrite);
					  buffer = new byte[CHUNK_LENGTH];
				}
				
				System.out.println("LOG: The converted file has been sent back.");
				
			} catch (SocketException e) {
				errorMessage = new String("Error in socket management while sending the positive response.");
				e.printStackTrace();
			} catch (IOException e) {
				errorMessage = new String("Error in sending the positive response.");
				e.printStackTrace();
			}
		} 
		//case 2: error
		else {
			try {
				//send the error code
				if(responseCode == 1)
					outputSocketStream.write('1');
				else if(responseCode == 2)
					outputSocketStream.write('2');
				
				//send the length of the error message
				int messageLength = errorMessage.length();
				outputSocketStream.writeInt(messageLength);
				
				//send the error message
				BufferedInputStream bisMessageToSend = new BufferedInputStream(new ByteArrayInputStream(errorMessage.getBytes()));
				byte[] buffer = new byte[CHUNK_LENGTH];
	            int bytesToWrite;
				while ((bytesToWrite = bisMessageToSend.read(buffer, 0, CHUNK_LENGTH)) != -1) {
					  outputSocketStream.write(buffer, 0, bytesToWrite);
					  buffer = new byte[CHUNK_LENGTH];
				}
				
				System.out.println("LOG: Error: " + errorMessage);
				System.out.println("LOG: The error message has been sent back.");
				
			} catch (SocketException e) {
				errorMessage = new String("Error in socket management while sending the negative response.");
				e.printStackTrace();
			} catch (IOException e) {
				errorMessage = new String("Error in sending the negative response.");
				e.printStackTrace();
			}
		}
		
		
		try {
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		} 

	}
}

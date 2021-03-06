package vn.com.vndirect.exchangesimulator;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import vn.com.vndirect.exchangesimulator.builder.FixMessageBuilder;
import vn.com.vndirect.exchangesimulator.datastorage.memory.InMemory;
import vn.com.vndirect.exchangesimulator.datastorage.queue.QueueInService;
import vn.com.vndirect.exchangesimulator.fixconvertor.FixConvertor;
import vn.com.vndirect.exchangesimulator.model.Logon;

@Component
public class TcpReceiver {

	private static final Logger log = Logger.getLogger(TcpReceiver.class);
	
	private List<Socket> sockets = new ArrayList<Socket>();

	private InMemory memory;
	
	private boolean isActive;
	
	private QueueInService queueInService;
	private FixConvertor fixConvertor;
	@Autowired
	public TcpReceiver(InMemory memory, QueueInService queueInService, FixConvertor fixConvertor) {
		this.memory = memory;
		this.queueInService = queueInService;
		this.fixConvertor = fixConvertor;
	}
	
	@PostConstruct
	public void start() {
		isActive = true;
	}
	
	public void stop() {
		isActive = false;
	}

	public void addSocket(Socket socket) throws IOException {
		if (isActive) {
			startSocket(socket);
		}
		sockets.add(socket);
	}

	private void startSocket(final Socket socket) throws IOException {
		final InputStream inputStream = socket.getInputStream();		
		Thread receiveThread = new Thread() {			
			@Override
			public void run() {
				FixMessageBuilder builder = new FixMessageBuilder();
				int bytesRead;
				byte[] buffer = new byte[1024];
				try {
					while ((bytesRead = inputStream.read(buffer)) != -1) {
						byte[] receivedBytes = new byte[bytesRead]; 
						System.arraycopy(buffer, 0, receivedBytes, 0, bytesRead);
						List<String> messages = builder.build(receivedBytes);
						receive(socket, messages);
						buffer = new byte[1024];
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				
			}


		};
		receiveThread.start();
	}

	private void acceptSocketWhenLogon(Socket socket, Logon logon) {
		acceptSocket(logon.getSenderCompID(), socket);
	}
	
	public void acceptSocket(String senderCompId, Socket socket) {
		log.info("Accept socket " + socket);
		memory.put("SocketClient", senderCompId, new SocketClient(socket, senderCompId));
		memory.put("sequence", senderCompId, 0);
		memory.put("last_processed_sequence", senderCompId, 0);
	}
	
	protected void receive(Socket socket, List<String> messages) {
		for (String message : messages) {
			Object object = fixConvertor.convertFixToObject(message);
			if (Logon.class.isInstance(object)) {
				acceptSocketWhenLogon(socket, (Logon) object);
			}
			log.info("EX <--: " + message);
			queueInService.add(object);
		}
	}

	public List<Socket> getSocketList() {
		return sockets;
	}

}

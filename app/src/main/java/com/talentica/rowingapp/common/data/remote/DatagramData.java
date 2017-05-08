package com.talentica.rowingapp.common.data.remote;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

public abstract class DatagramData {
	
	private String address;
	
	private int port;
		
	private Thread connectionThread;
	
	private boolean stopRequested;
		
	final DatagramSocketHelper dsh = new DatagramSocketHelper();
	
	private DatagramSocket socket;
	
	private final DatagramSocketType type;
	
	protected DatagramData(DatagramSocketType type, String address, int port) throws DataRemote.DataRemoteError {
		this.type = type;
		this.address = address;
		this.port = port;
	}

	public synchronized void stop() {
				
		stopRequested = true;
		
		if (socket != null) {
			socket.close();
		}
		
		if (connectionThread != null) {
			connectionThread.interrupt();
			
			try {
				connectionThread.join();
			} catch (InterruptedException e) {
			}
						
			socket = null;			
		}
		
		stopRequested = false;
	}

	public boolean isConnected() {
		return !stopRequested && socket != null;
	}
	

	public synchronized void start() {
		

		final String name = getClass().getSimpleName();
		final Object startSync = this;
		
		connectionThread = new Thread(name) {
			public void run() {					
								
				while (!stopRequested) {
					try {
						
						if (socket == null) {
							synchronized (startSync) {
								try {

									socket = dsh.createSocket(type, address, port);
									
								} finally {
									startSync.notifyAll();
								}
							}							
						}
						
						
						processNextItem(dsh);
						
					} catch (Exception e) {
						if (!stopRequested) {
							Log.e("connectionThread", "remote data reading error - receiver loop continues", e);
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e1) {
							}
						}
					}
				}
			}
		};
		
		synchronized (startSync) {
			
			connectionThread.start();
			
			try {
				startSync.wait();
			} catch (InterruptedException e) {

			}
		}
	}
	
	protected abstract void processNextItem(DatagramSocketHelper dsh) throws IOException;	
	
	private synchronized void restart() {
		
		boolean alreadyStarted = socket != null;
		
		if (alreadyStarted) {
			stop();
			start();
		}
	}

	public void setPort(int port) {		
		this.port = port;		
		restart();		
	}

	public void setAddress(String address) {
		this.address = address;
		restart();
	}
	
	public boolean isMulticast() throws DataRemote.DataRemoteError {
		try {
			return InetAddress.getByName(address).isMulticastAddress();
		} catch (UnknownHostException e) {
			throw new DataRemote.DataRemoteError(e);
		}
	}
}

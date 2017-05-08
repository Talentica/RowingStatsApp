package com.talentica.rowingapp.common.data.remote;


public interface DataSender extends DataRemote {
	/**
	 * Send a data item - non blocking
	 * @param data item to send
	 */
	public void write(String data);
}

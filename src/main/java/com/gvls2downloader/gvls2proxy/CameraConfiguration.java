package com.gvls2downloader.gvls2proxy;

/**
 * Contains info regarding a connection to a specific camera (or camera simulator).
 * 
 */
public class CameraConfiguration {
	private String ip;
	private int port = 80;
	private String userID;
	private String password;
	private String basePath = "/";

	public CameraConfiguration() {
	}
	
	public CameraConfiguration(String ip, int port, String basePath, String userID, String password) {
		this.ip = ip;
		this.port = port;
		this.userID = userID;
		this.password = password;
		this.basePath = basePath;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getUserID() {
		return userID;
	}

	public void setUserID(String userID) {
		this.userID = userID;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getBasePath() {
		return basePath;
	}

	public void setBasePath(String basePath) {
		this.basePath = basePath;
	}
	
	/**
	 * Equality checks are based on the ip, port, and basePath
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((basePath == null) ? 0 : basePath.hashCode());
		result = prime * result + ((ip == null) ? 0 : ip.hashCode());
		result = prime * result + port;
		return result;
	}
	/**
	 * Equality checks are based on the ip, port, and basePath
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CameraConfiguration other = (CameraConfiguration) obj;
		if (basePath == null) {
			if (other.basePath != null)
				return false;
		} else if (!basePath.equals(other.basePath))
			return false;
		if (ip == null) {
			if (other.ip != null)
				return false;
		} else if (!ip.equals(other.ip))
			return false;
		if (port != other.port)
			return false;
		return true;
	}
	

	@Override
	public String toString() {
		return "RequestInfo [ip=" + ip + ", port=" + port + ", userID="
				+ userID + ", password=" + password + ", basePath=" + basePath
				+ "]";
	}	
}

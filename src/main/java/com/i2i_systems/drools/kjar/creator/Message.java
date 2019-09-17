package com.i2i_systems.drools.kjar.creator;

public class Message {
	public String type;
	public String message;

	public Message(String type, String message) {
		this.type = type;
		this.message = message;
	}

	public String printMessage() {
		return "TYPE=" + type + " MESSAGE=" + message;
	}
}

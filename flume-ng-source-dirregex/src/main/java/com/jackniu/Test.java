package com.jackniu;

import com.google.gson.Gson;


public class Test {
	public static void main(String[] args)
	{
		Json json = new Json();
		json.fileName = "/opt/flume";
		json.logBody ="ip";
		json.ip = "0.0.0.0";                                  // 但是这里的IP还是有点问题
		Gson gson = new Gson();
		String eventString = gson.toJson(json);
		System.out.println(eventString.toString());
	}
	
}
class Json{
	String fileName;    // 业务名称
	String logBody ;    // 日志体
	String ip;
}
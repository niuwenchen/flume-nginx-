/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.changhong.bigdata.flume.source.dirregex;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
//import java.sql.Date;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.flume.ChannelException;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDrivenSource;
import org.apache.flume.conf.Configurable;
import org.apache.flume.event.EventBuilder;
import org.apache.flume.instrumentation.SourceCounter;
import org.apache.flume.source.AbstractSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
/**
 * @ClassName:DirRegexSource
 * @Decription:递归监控目录，按字节数核对重启续读.断点续传，正则匹配内容提取数据，正则匹配文件名，内存使用上限tolalMemory大于
 * maxMemory的0.4
 * @author YuYue
 * @date 2016-01-13 11:40:22
 * */

public class DirRegexSource extends AbstractSource implements EventDrivenSource, Configurable {
	private static final Logger logger = LoggerFactory.getLogger(DirRegexSource.class);
	private File monitorDir, checkFile;
	private Pattern monitorFilePattern, contentPattern;
	private long delayTime;
	private String charsetName;
	private int batchSize;
	private Properties properties;
	private Properties tmpProperties = new Properties();
	private ScheduledExecutorService scheduledExecutorService;
	private ExecutorService executorService;
	private SourceCounter sourceCounter;
	private String ipstr;
	
	private String DEFAULT_MONITORFILEREGEX = "[\\W\\w]+";
	private String DEFAULT_CHARSETNAME = "UTF-8";
	private long DEFAULT_DELAYTIME = 10l;
	private int DEFAULT_BATCHSIZE = 1024;
//	private String partFileName;
//	private String currentTime;
/**
  * @Title: configure
  * @Description: 获取flume配置文件内容
  * @author YuYue
  * @param context   
  * @throws
*/
	public void configure(Context context) {
		logger.info("----------------------DirRegexSource configure...");
		try {
			// monitorDir、monitorFileRegex
			String strMonitorDir = context.getString("monitorDir");
			Preconditions.checkArgument(StringUtils.isNotBlank(strMonitorDir), "Missing Param:'monitorDir'");
			String monitorFileRegex = context.getString("monitorFileRegex", DEFAULT_MONITORFILEREGEX);
			Preconditions.checkArgument(StringUtils.isNotBlank(monitorFileRegex), "Missing Param:'monitorFileRegex'");
			monitorFilePattern = Pattern.compile(monitorFileRegex);
			// checkFile
			String strCheckFile = context.getString("checkFile");
			Preconditions.checkArgument(StringUtils.isNotBlank(strCheckFile), "Missing Param:'checkFile'");
			
			// contentRegex
			String contentRegex = context.getString("contentRegex");
			Preconditions.checkArgument(StringUtils.isNotBlank(contentRegex), "Missing Param:'contentRegex'");
			contentPattern = Pattern.compile(contentRegex);
			// ip
			ipstr = context.getString("ip");
			Preconditions.checkArgument(StringUtils.isNotBlank(ipstr), "Missing Param:'contentRegex'");
			
			// delayTime、charsetName、batchSize
			delayTime = context.getLong("delayTime", DEFAULT_DELAYTIME);
			Preconditions.checkArgument(delayTime > 0, "'delayTime' must be greater than 0");
			charsetName = context.getString("charsetName", DEFAULT_CHARSETNAME);
			Preconditions.checkArgument(StringUtils.isNotBlank(charsetName), "Missing Param:'charsetName'");
			batchSize = context.getInteger("batchSize", DEFAULT_BATCHSIZE);
			Preconditions.checkArgument(batchSize > 0, "'batchSize' must be greater than 0");
			
			monitorDir = new File(strMonitorDir);
			checkFile = new File(strCheckFile);

			properties = new Properties();
			if (!checkFile.exists()) {
				checkFile.createNewFile();	
			} else {
				FileInputStream checkfile001 =new FileInputStream(checkFile);
				properties.load(checkfile001);
				checkfile001.close();
			}

			executorService = Executors.newCachedThreadPool();
			scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
			sourceCounter = new SourceCounter("DirRegexSource");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			throw new IllegalArgumentException(e);
		}
		logger.info("----------------------DirRegexSource configured!");
	}
	/**
	  * @Title: start
	  * @Description: 启动方法
	  * @author YuYue
	  * @param start()   
	  * @throws
	*/
	public void start() {
		logger.info("----------------------DirRegexSource starting...");
		sourceCounter.start();
		Runnable dirRunnable = new DirRunnable(monitorDir);
		scheduledExecutorService.scheduleWithFixedDelay(dirRunnable, 0, delayTime, TimeUnit.SECONDS);
		super.start();
		logger.info("----------------------DirRegexSource started!");
	}
	/**
	  * @Title: configure
	  * @Description: 停止方法
	  * @author YuYue
	  * @param stop()   
	  * @throws
	*/
	public void stop() {
		logger.info("----------------------DirRegexSource stopping...");
		scheduledExecutorService.shutdown();
		executorService.shutdown();
		try {
			scheduledExecutorService.awaitTermination(10L, TimeUnit.SECONDS);
			executorService.awaitTermination(10L, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		scheduledExecutorService.shutdownNow();
		executorService.shutdown();
		sourceCounter.stop();
		super.stop();
		logger.info("----------------------DirRegexSource stopped!");
	}
	/**
	  * @Title: DirRunnable
	  * @Description: 目录监控线程,匹配时间日志文件格式为"myDefine.log-2016-01-17"
	  * @Description:匹配规则可以改变。"yyyy-MM-dd"，这只写成了监控当天日期的文件
	  * @author YuYue
	  * @param runnable   
	  * @throws
	*/
	private class Json{
		String filename;
		String ip ;
		String body;
	}
	private class DirRunnable implements Runnable {
		private File monitorDir;

		DirRunnable(File monitorDir) {
			this.monitorDir = monitorDir;
		}

		public void run() {
			logger.debug("----------------------dir monitor start...");
			monitorFile(monitorDir);
			logger.debug("----------------------dir monitor stoped");
		}
		
		private void monitorFile(File dir) {
			for (File tmpFile : dir.listFiles()) {
//				partFileName = tmpFile.getName().substring(tmpFile.getName().length()-10,
//						tmpFile.getName().length());
//				Date dt = new Date();
//				SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
//				currentTime = format.format(dt);
//				if(currentTime.equals(partFileName)){
//					 logger.debug("------------------------------dir name after matches");
				 if (tmpFile.isFile() && !tmpProperties.containsKey(tmpFile.getPath())) {
//					 logger.info("------------------------------dir matches");
					Matcher matcher = monitorFilePattern.matcher(tmpFile.getName());
					if (matcher.matches()) {
//						logger.info("-------------------dir matched");
						Runnable fileRunnable = null;
						// new file
						if (!properties.containsKey(tmpFile.getPath())) {
							fileRunnable = new FileRunnable(tmpFile, 0);
							tmpProperties.put(tmpFile.getPath(), "0");
						} else {
							int readedLength = Integer.valueOf(properties.get(tmpFile.getPath()).toString());
							// changed file
							if (readedLength < tmpFile.length()) {	
								fileRunnable = new FileRunnable(tmpFile, readedLength);
								tmpProperties.put(tmpFile.getPath(), readedLength + "");
								// roll file
							} else if (readedLength > tmpFile.length()) {
								fileRunnable = new FileRunnable(tmpFile, 0);
								tmpProperties.put(tmpFile.getPath(), "0");
								// unchanged file
							} else {
								continue;
							}
						}
						executorService.submit(fileRunnable);
						continue;
					}
//				} else if (tmpFile.isDirectory()) {
//					monitorFile(tmpFile);
//				}
			  }else if(tmpFile.isDirectory()){
				  monitorFile(tmpFile);
			  }
			}
		}
	}
	/**
	  * @Title: FileRunnable
	  * @Description: 文件监控线程，实时读取文件内容，可以编辑contentRegex的值来实现内容正则匹配，.find()方法匹配
	  * @author YuYue
	  * @param runnable   
	  * @throws
	*/
	private class FileRunnable implements Runnable {
		private File monitorFile;
		private int readedLength;

		FileRunnable(File monitorFile, int readedLength) {
			this.monitorFile = monitorFile;
			this.readedLength = readedLength;
		}

		public void run() {
			logger.debug("----------------------file monitor start...");
			logger.info("----------------------read {}", monitorFile.getPath());

			FileInputStream fis = null;
			try {
				// read file(read in batches)
				StringBuilder strBuilder = new StringBuilder();
				fis = new FileInputStream(monitorFile);
				int readed = 0;
				int mark = 0;
				fis.skip(readedLength);
				byte[] arrByte = new byte[1024 * 1024];
				long freeMemory = Runtime.getRuntime().freeMemory();
				while (readed + readedLength < monitorFile.length()) {
					int read = 0;
					while ((read = fis.read(arrByte)) != -1) {
						if (arrByte.length > read) {
							strBuilder.append(new String(arrByte, 0, read, charsetName));
						} else {
							strBuilder.append(new String(arrByte, charsetName));
						}
						readed += read;
						if (Runtime.getRuntime().totalMemory() == Runtime.getRuntime().maxMemory() || (Runtime.getRuntime().totalMemory() > Runtime.getRuntime().maxMemory() * 0.4 && Runtime.getRuntime().freeMemory() > freeMemory)) {
							freeMemory = Runtime.getRuntime().freeMemory();
							break;
						}
						freeMemory = Runtime.getRuntime().freeMemory();
					}
					logger.debug("----------------------get {} byte data", readed - readedLength);

					// create events(remove the last event)
					List<Integer> numList = new ArrayList<Integer>();
					Matcher contentMatcher = contentPattern.matcher(strBuilder.toString());
					List<Event> eventList = new ArrayList<Event>();
//					List<StrToJson> strToJsons =new ArrayList<StrToJson>();
					
					while (contentMatcher.find()) {
						String contentString = contentMatcher.group(1);
						String filenameString = monitorFile.getName();
						
						//transform to json
						Json json = new Json();
						json.filename = filenameString;
						json.body = contentString;
						json.ip = ipstr;
						Gson gson = new Gson();
						String eventString = gson.toJson(json);
						
						//add event to evenlist
//						contentString = "["+filenameString+"]" + " " +contentString;
						Event event = EventBuilder.withBody(eventString.getBytes());	
						event.getHeaders().put("filePath", monitorFile.getPath());
						eventList.add(event);
						numList.add(contentMatcher.end(1));
						mark = contentMatcher.start(1);
					}
					if (readed + readedLength < monitorFile.length() && eventList.size() > 0) {
						eventList.remove(eventList.size() - 1);
						numList.remove(numList.size() - 1);
					}
					logger.debug("----------------------create {} events", eventList.size());

					// process events(process in batches)
					if (eventList.size() != 0) {
						sourceCounter.addToEventReceivedCount(eventList.size());
						int batchCount = eventList.size() / batchSize + 1;
						try {
							for (int i = 0; i < batchCount; i++) {
								if (i != batchCount - 1) {
									tmpProperties.put(monitorFile.getPath(), (readedLength + numList.get((i + 1) * batchSize - 1)) + "");
									sourceCounter.addToEventAcceptedCount(eventList.subList(i * batchSize, (i + 1) * batchSize).size());
									getChannelProcessor().processEventBatch(eventList.subList(i * batchSize, (i + 1) * batchSize));
								} else {
									tmpProperties.put(monitorFile.getPath(), (readed + readedLength) + "");
									sourceCounter.addToEventAcceptedCount(eventList.subList(i * batchSize, eventList.size()).size());
									getChannelProcessor().processEventBatch(eventList.subList(i * batchSize, eventList.size()));
								}
							}
						} catch (ChannelException ex) {
							// TODO Auto-generated catch block
							ex.printStackTrace();
						}
						logger.debug("----------------------process {} batchs", batchCount);
					} else {
						tmpProperties.put(monitorFile.getPath(), (readed + readedLength) + "");
					}
					if (mark == 0) {
						strBuilder.setLength(0);
					} else {
						strBuilder.delete(0, mark);
					}
					logger.debug("----------------------file monitor stoped");
				}
				fis.close();
			} catch (Throwable e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				properties.put(monitorFile.getPath(), tmpProperties.get(monitorFile.getPath()));
				try {
					FileOutputStream checkfile = new FileOutputStream(checkFile);
					properties.store(checkfile, null);
					checkfile.close();
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				tmpProperties.remove(monitorFile.getPath());
			}
		}
	}
}

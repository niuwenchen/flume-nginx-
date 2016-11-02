package com.changhong.bigdata.flume.source.dirnginx;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
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


/**
 * @ClassName:NginxSource
 * @Decription:监控目录Nginx 目录，按字节数核对重启续读.断点续传，正则匹配内容提取数据，正则匹配文件名，内存使用上限tolalMemory大于
 * maxMemory的0.4
 * @author Niuwenchen
 * @date 2016-10-26 15:00:00
 * */

public class NginxSource extends AbstractSource implements EventDrivenSource, Configurable{

	private Logger logger= LoggerFactory.getLogger(NginxSource.class);
	private File monitorDir,checkFile; 									// 监控目录
	private Pattern monitorFilePattern, contentPattern;                 // 文件名  文件内容
//	private long delayTime;
	private String charsetName;
	private int batchSize;
	private ScheduledExecutorService executor;
	private SourceCounter sourceCounter;
	private int anchor=0;
	private Properties properties;
	private Properties tmpProperties = new Properties();

	
	private String DEFAULT_MONITORFILEREGEX = "[\\W\\w]+";
	private String DEFAULT_CHARSETNAME = "UTF-8";
	private long DEFAULT_DELAYTIME = 10l;
	private int DEFAULT_BATCHSIZE = 1024;
	
	
	/**
	  * @Title: configure
	  * @Description: 获取flume配置文件内容
	  * @author Niuwenchen
	  * @param context   
	  * @throws
	*/
	@Override
	public void configure(Context context) {
		logger.info("----------------------NginxSource configure...");
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

			
			executor = Executors.newSingleThreadScheduledExecutor();
			sourceCounter = new SourceCounter("NginxSource");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			throw new IllegalArgumentException(e);
		}
		logger.info("----------------------NginxSource configured!");
		
	}

	/**
	  * @Title: start
	  * @Description: 启动方法
	  * @author Niuwenchen
	  * @param start()   
	  * @throws
	*/

	@Override
	public  void start() {
		logger.info("----------------------NginxSource starting...");
		sourceCounter.start();
		Runnable dirRunnable = new DirRunnable(monitorDir);
		executor.scheduleWithFixedDelay(dirRunnable, 0, 10, TimeUnit.SECONDS);
		super.start();
		logger.info("----------------------NginxSource started!");
	}


	@Override
	public  void stop() {
		logger.info("----------------------DirRegexSource stopping...");
		executor.shutdown();
		executor.shutdown();
		try {
			executor.awaitTermination(10L, TimeUnit.SECONDS);
			executor.awaitTermination(10L, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		executor.shutdownNow();
		executor.shutdown();
		sourceCounter.stop();
		super.stop();
		logger.info("----------------------DirRegexSource stopped!");
	}
	
	
	private class Json{
		String fileName;    // 业务名称
		String logBody ;    // 日志体
		String dirName ;    // 日志文件目录
	}
	
	
	/**
	  * @Title: DirRunnable
	  * @Description: 目录监控线程，递归获取目录，并启动该目录下文件的读取
	  * @author Niuwenchen
	  * @param runnable   
	  * @throws
	*/
	
	private class DirRunnable implements Runnable {
		private File monitorDir;

		DirRunnable(File monitorDir) {
			this.monitorDir = monitorDir;
		}

		@Override
		public void run() {
			logger.debug("----------------------dir monitor start...");
			monitorFile(monitorDir);
			logger.debug("----------------------dir monitor stoped");
			
		}

		private void monitorFile(File dir) {
			for (File tmpFile : dir.listFiles()) {
				
				if(tmpFile.isFile() && !tmpProperties.containsKey(tmpFile.getPath()))
				{
					Matcher matcher = monitorFilePattern.matcher(tmpFile.getName());
					if(matcher.matches())
					{
						Runnable fileRunnable = null;
						if (!properties.containsKey(tmpFile.getPath())) {
							fileRunnable = new FileRunnable(tmpFile, 0);
							tmpProperties.put(tmpFile.getPath(), "0");
						} 
						else
						 {
							int readedLength = Integer.valueOf(properties.get(tmpFile.getPath()).toString());
							
							if (readedLength < tmpFile.length()) {	
								fileRunnable = new FileRunnable(tmpFile, readedLength);
								tmpProperties.put(tmpFile.getPath(), readedLength + "");
								// roll file
							} else if (readedLength > tmpFile.length()) { // 当该文件被删除 或者是压缩后重命名 则需要从头读取
								fileRunnable = new FileRunnable(tmpFile, 0);
								tmpProperties.put(tmpFile.getPath(), "0");
								// unchanged file
							} else {
								continue;
							}
						}
						executor.submit(fileRunnable);
						continue;	
					}
				}
				else if(tmpFile.isDirectory())
				{
						monitorFile(tmpFile);
				}
				}	
			}
	}
	
	/**
	  * @Title: FileRunnable
	  * @Description: 文件监控线程，实时读取文件内容，可以编辑contentRegex的值来实现内容正则匹配，.find()方法匹配
	  * @author Niuwenchen
	  * @param runnable   
	  * @throws
	*/
	private class FileRunnable implements Runnable 
	{
		private File monitorFile;
		private int readedLength;

		FileRunnable(File monitorFile, int readedLength) {
			this.monitorFile = monitorFile;
			this.readedLength = readedLength;
		}

		@Override
		public void run() {
			logger.debug("----------------------file monitor start...");
			logger.info("----------------------read {}", monitorFile.getPath());
			FileInputStream fis = null;
			try{
				StringBuilder strBuilder = new StringBuilder();
				fis = new FileInputStream(monitorFile);
				fis.skip(readedLength);
				byte[] arrByte = new byte[1024 * 1024];
				long freeMemory = Runtime.getRuntime().freeMemory();
				int readed = 0;
				int mark = 0;
				
				while (readed + readedLength < monitorFile.length()) 
				{	
					int read = 0;
					while ((read = fis.read(arrByte)) != -1) {

						strBuilder.append(new String(arrByte,0,read,charsetName));
						
						readed += read;
						if (Runtime.getRuntime().totalMemory() == Runtime.getRuntime().maxMemory() || (Runtime.getRuntime().totalMemory() > Runtime.getRuntime().maxMemory() * 0.4 && Runtime.getRuntime().freeMemory() > freeMemory)) {
							freeMemory = Runtime.getRuntime().freeMemory();
							break;
						}
						
						freeMemory = Runtime.getRuntime().freeMemory();	
					}
					logger.debug("----------------------get {} byte data", readed - readedLength);
					
					
					List<Integer> numList = new ArrayList<Integer>();
					List<Event> eventList = new ArrayList<Event>();
					Matcher contentMatcher = contentPattern.matcher(strBuilder.toString());
					
					while (contentMatcher.find()) {
						String contentString = contentMatcher.group(0);   // 这里有一点问题
						String filenameString = monitorFile.getName();
					
						//transform to json
						Json json = new Json();
						json.fileName = filenameString;
						json.logBody = contentString;
						json.dirName=monitorDir.getAbsolutePath();
						Gson gson = new Gson();
						String eventString = gson.toJson(json);
						
						
						Event event = EventBuilder.withBody(eventString.getBytes());	
						event.getHeaders().put("filePath", monitorFile.getPath());
						eventList.add(event);
						numList.add(contentMatcher.end(0));
						mark = contentMatcher.start(0);	
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
					fis.close();
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


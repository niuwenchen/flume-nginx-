package com.jackniu;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

public class TestEventSource extends AbstractSource implements EventDrivenSource, Configurable {
	private Logger logger= LoggerFactory.getLogger(TestEventSource.class);
	private File monitorDir,checkFile; // 监控目录
	private int batchSize;
	private ScheduledExecutorService executor;
	private SourceCounter sourceCounter;
	private int anchor=0;
	private Properties properties;
	private Properties tmpProperties = new Properties();
	
	
	@Override
	public void configure(Context context) {
		logger.info("----------------------TestEventSource configure...");
		String strMonitorDir = context.getString("monitorDir");
		Preconditions.checkArgument(StringUtils.isNotBlank(strMonitorDir), "Missing Param:'monitorDir'");
		batchSize = context.getInteger("batchSize");
		Preconditions.checkArgument(batchSize > 0, "'batchSize' must be greater than 0");
		
		String strCheckFile = context.getString("checkFile");
		Preconditions.checkArgument(StringUtils.isNotBlank(strCheckFile),"Missing param: 'checkFile'");
		
		
		logger.info("----------------------TestEventSource configure..."+strMonitorDir);
		
		monitorDir=new File(strMonitorDir);
		checkFile = new File(strCheckFile);
		
		try{
			
	
		properties = new Properties();
		if (!checkFile.exists()) {
			checkFile.createNewFile();	
		} else {
			FileInputStream checkfile001 =new FileInputStream(checkFile);
			properties.load(checkfile001);
			checkfile001.close();
		}
		
		}catch(Exception e)
		{
			e.printStackTrace();
		}
		
		
		
		executor = Executors.newSingleThreadScheduledExecutor();
		sourceCounter = new SourceCounter("TestEventSource");
		logger.info("----------------------TestEventSource configured!");
	}

	@Override
	public synchronized void start() {
		// TODO Auto-generated method stub
		logger.info("----------------------DirRegexSource starting...");
		sourceCounter.start();
		
		Runnable dirRunnable = new DirRunnable(monitorDir);
		executor.scheduleWithFixedDelay(dirRunnable, 0, 10, TimeUnit.MINUTES);
		super.start();
		logger.info("----------------------DirRegexSource started!");
	}

	@Override
	public synchronized void stop() {
		// TODO Auto-generated method stub
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
				Runnable fileRunnable = null;
				if(tmpFile.isFile())
				{
					if (!properties.containsKey(tmpFile.getPath())) {
						fileRunnable = new FileRunnable(tmpFile, 0);
						tmpProperties.put(tmpFile.getPath(), "0");
					}
					else{
					
						int readedLength = Integer.valueOf(properties.get(tmpFile.getPath()).toString());
						if(readedLength < tmpFile.length())
						{
					
							fileRunnable = new FileRunnable(tmpFile, readedLength);
							tmpProperties.put(tmpFile.getPath(), readedLength + "");
						}else if(readedLength >= tmpFile.length())
						{
							fileRunnable = new FileRunnable(tmpFile, 0);
							tmpProperties.put(tmpFile.getPath(), "0");
						}
						else
						{
							continue;
						}
						}
					executor.submit(fileRunnable);
					
				}
				else
				{
					if(tmpFile.isDirectory())
					{
						monitorFile(tmpFile);
					}
				}
			}
			
		}
		
	}
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
				byte[] arrByte = new byte[1024];
				long freeMemory = Runtime.getRuntime().freeMemory();
				int readed = 0;
				while (readed + readedLength < monitorFile.length()) 
				{	
					int read = 0;
					while ((read = fis.read(arrByte)) != -1) {

						strBuilder.append(new String(arrByte,0,read,"UTF-8"));
						
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
					String contentString=strBuilder.toString();
					// 构造Event事件
					Event event = EventBuilder.withBody(contentString.getBytes());	
					event.getHeaders().put("filePath", monitorFile.getPath());
					
					eventList.add(event);
					
					if(eventList.size()!=0)
					{
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
					}
					else {
						tmpProperties.put(monitorFile.getPath(), (readed + readedLength) + "");
					}
					logger.debug("----------------------file monitor stoped");
					
			}
			}catch(Exception e)
			{
				e.printStackTrace();
			}finally {
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

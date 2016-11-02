package com.jackniu;

import java.io.File;
import java.io.FileFilter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.flume.Context;
import org.apache.flume.CounterGroup;
import org.apache.flume.Event;
import org.apache.flume.client.avro.ReliableSpoolingFileEventReader;
import org.apache.flume.conf.Configurable;
import org.apache.flume.instrumentation.SourceCounter;
import org.apache.flume.source.AbstractSource;
import org.apache.flume.source.SpoolDirectorySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class SpoolSource extends SpoolDirectorySource implements Configurable {
	 private static Logger logger = LoggerFactory.getLogger(SpoolSource.class);
	private String spoolDirectory;
	private String completedSuffix; // 文件读取完毕的后缀
//	private String fileHeader; // 是否在event的HEader中添加文件名
//	private String fileHeaderKey;  // 这是event的Header中的key，value时文件名
	private int batchSize;     //一次处理的记录数目  默认为100
	
//	private String inputCharset; // 编码方式，默认时UTF-8
//	private String ignorePattern;  // 忽略复合物条件的文件名
//	private String trackerDirPath;  // 被处理文件元数据的存储目录，默认时.flumespool

 	private CounterGroup counterGroup;
 	private ReliableSpoolingFileEventReader reader;
	private SourceCounter sourceCount;
 	
	@Override
	public synchronized void configure(Context context) {
		logger.info("**************Configure********************");
		spoolDirectory=context.getString("spoolDir");
		Preconditions.checkState(spoolDirectory!=null,
				"Configuration must specity a apooling directory");
		completedSuffix=context.getString("completedSuffix",".COMPLETED");
		batchSize=context.getInteger("batchSize",100);
		logger.info("***"+spoolDirectory+"  *****"+completedSuffix+"********"+batchSize);
		sourceCount = getSourceCounter();
		
	}

	@Override
	public synchronized void start() {
		logger.info("***********start*****************");
		ScheduledExecutorService  executor = Executors.newSingleThreadScheduledExecutor();
		
		counterGroup=new CounterGroup();
		File directory = new File(spoolDirectory);
		try{
			reader=new ReliableSpoolingFileEventReader.Builder()
					.spoolDirectory(directory)
					.completedSuffix(completedSuffix)
					.build();
		}catch(Exception e)
		{
			e.printStackTrace();
		}
		//构建了一个org.apache.flume.client.avro.ReliableSpoolingFileEventReader的对象reader
		Runnable runner= new SpoolDirectoryRunnable(reader,counterGroup);
		executor.scheduleWithFixedDelay(runner, 0, 500, TimeUnit.MILLISECONDS);
		
		logger.debug("SpoolDirectorySource source started");
		
	}

	
	
	
	@Override
	public synchronized void stop() {
		// TODO Auto-generated method stub
		super.stop();
	}
	
	
	private void listDirFiles(List<File> files,File dir,FileFilter filter)
	{
		File[] childs = dir.listFiles();
		for (int i=0;i< childs.length;i++) // 列出目录下所有的文件
		{
			if(childs[i].isFile())
				files.add(childs[i]);
			else
			{
				if(childs[i].isDirectory())
					listDirFiles(files,childs[i],filter); // 如果是个目录,就继续递归列出其下面的文件.
			}
				
		}
	}
	 
	// 读取并发送event进程
private	class SpoolDirectoryRunnable implements Runnable{
		private ReliableSpoolingFileEventReader  reader;
		private CounterGroup counterGroup;
		
		public SpoolDirectoryRunnable(ReliableSpoolingFileEventReader reader,CounterGroup counterGroup)
		{
			this.reader=reader;
			this.counterGroup=counterGroup;
		}
		
		@Override
		public void run() {
			try{
				while(true)
				{
					List<Event> events = reader.readEvents(batchSize);
					if(events.isEmpty())
						break;
				
				counterGroup.addAndGet("spooler.events.read", (long)events.size());
				//将events批量发送到channel
				getChannelProcessor().processEventBatch(events);
//				logger.info("*****SourceCounter*******"+sourceCount.getAppendBatchAcceptedCount());
				reader.commit();
			}
			
			}catch(Exception e)
			{
			logger.error("Uncaught exception in Runnable ",e);
			
			}
		
	}	
}

}


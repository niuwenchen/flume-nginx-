/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.changhong.bigdata.flume.source.dirregex.test;

import java.util.ArrayList;
import java.util.List;

import org.apache.flume.Channel;
import org.apache.flume.ChannelSelector;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.Transaction;
import org.apache.flume.channel.ChannelProcessor;
import org.apache.flume.channel.MemoryChannel;
import org.apache.flume.channel.ReplicatingChannelSelector;
import org.apache.flume.conf.Configurables;
import org.junit.Before;
import org.junit.Test;

import com.changhong.bigdata.flume.source.dirregex.DirRegexSource;

public class TestDirRegexSource {
	private Context context;
	private DirRegexSource source;
	private Channel channel;

	@Before
	public void before() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		context = new Context();
		context.put("monitorDir", "E:\\flume");
		context.put("monitorFileRegex", "hybase\\d{1}");
		context.put("checkFile", "E:\\data\\flume\\check");
		context.put("contentRegex", "(<REC>\r\n(?:<[^>]*>=(?:(?!\r\n)[\\W\\w])*\r\n)+)");
		
		Context context1 = new Context();
		context1.put("checkpointDir", "E:\\data\\flume\\checkPoint");
		context1.put("dataDirs", "E:\\data\\flume\\data");
		context1.put("capacity", "20000000");
		context1.put("transactionCapacity", "2000000");


		source = new DirRegexSource();
		channel = new MemoryChannel();
		channel.setName("1");
		Configurables.configure(channel, context1);

		List<Channel> channels = new ArrayList<Channel>();
		channels.add(channel);

		ChannelSelector rcs = new ReplicatingChannelSelector();
		rcs.setChannels(channels);

		source.setChannelProcessor(new ChannelProcessor(rcs));
		channel.start();
	}

	@Test
	public void testSink() throws InterruptedException {
		source.configure(context);
		source.start();
		Thread channelRunnable = new Thread(new ChannelRunnable());
		channelRunnable.run();
//		MemoryRunnable memoryRunnable = new MemoryRunnable();
//		memoryRunnable.run();
	}
	
	class ChannelRunnable implements Runnable {
		public void run() {
			while (true) {
				Transaction transaction = channel.getTransaction();
				transaction.begin();
				Event event;
				try {
					int num=0;
				    while ((event = channel.take()) != null){
//						System.out.println(event);
//						System.out.println(new String(event.getBody()));
//						System.out.println("-------------------------");
						num++;
				    }
					System.out.println(num);
				    transaction.commit();
				} catch (Throwable t) {
					transaction.rollback();
					System.out.println(t);
				} finally {
					transaction.close();
				}
			}
		}
	}
	
	class MemoryRunnable implements Runnable {
		public void run() {
			while (true) {
				System.out.print("maxMemory:"+Runtime.getRuntime().maxMemory());
				System.out.print(",freeMemory:"+Runtime.getRuntime().freeMemory());
				System.out.println(",totalMemory:"+Runtime.getRuntime().totalMemory());
			    try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
}

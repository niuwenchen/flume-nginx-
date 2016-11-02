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

import org.junit.Test;

public class TmpTest {
	@Test
	public void test1() {
		Thread thread1 = new Thread(new FileRunnable());
		Thread thread2 = new Thread(new FileRunnable());
		Runnable thread3 = new FileRunnable();
		thread1.start();
		thread2.start();
		thread3.run();
	}

	private static Object getFreeMemory() {
		return Runtime.getRuntime().maxMemory() + ":" + Runtime.getRuntime().totalMemory() + ":" + Runtime.getRuntime().freeMemory();
		// long maxMemory = Runtime.getRuntime().maxMemory();
		// long totalMemory = Runtime.getRuntime().totalMemory();
		// long freeMemory = Runtime.getRuntime().freeMemory();
		// // Some JDKs (JRocket) return 0 for the maxMemory
		// if (maxMemory < totalMemory)
		// return freeMemory;
		// else
		// return maxMemory - totalMemory + freeMemory;
	}

	private class FileRunnable implements Runnable {
		@Override
		public void run() {
			StringBuilder stringBuilder = new StringBuilder();
			StringBuilder stringBuilder1 = new StringBuilder();
			long freeMemory = Runtime.getRuntime().freeMemory();
			try {
				while (true) {
					for (int i = 0; i < 8000; i++) {
						byte[] byteArr = new byte[1024 * 1024];
						stringBuilder.append(new String(byteArr));
						System.out.println(i + "----" + getFreeMemory());
						if (Runtime.getRuntime().totalMemory() > Runtime.getRuntime().maxMemory() * 0.4 && Runtime.getRuntime().freeMemory() > freeMemory) {
							freeMemory = Runtime.getRuntime().freeMemory();
							System.out.println(i);
							break;
						}
						freeMemory = Runtime.getRuntime().freeMemory();
					}
					StringBuilder stringBuilder0 = new StringBuilder();
					stringBuilder0.append(stringBuilder);
					stringBuilder1.append(stringBuilder);
					stringBuilder.setLength(0);
				}
			} catch (Throwable e) {
				// TODO Auto-generated catch block
				System.out.println("溢出----" + getFreeMemory());
				e.printStackTrace();
			}
		}

	}
}

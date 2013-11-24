package com.centurywar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.centurywar.control.MessageControl;

public class Main {
	protected final static Log Log = LogFactory.getLog(Main.class);
	private int port = 8080;
	private ServerSocket serverSocket;
	private ExecutorService executorService;
	private final int POOL_SIZE = 10;
	public static Map<String, MainHandler> globalHandler = new HashMap<String, MainHandler>();
	public static Map<String, MainHandler> arduinoHandler = new HashMap<String, MainHandler>();
	public static Map<String, MainHandler> temHandler = new HashMap<String, MainHandler>();
	private static int MaxTem = 1;
	
	public Main() throws IOException {
		serverSocket = new ServerSocket(port);
		executorService = Executors.newFixedThreadPool(Runtime.getRuntime()
				.availableProcessors() * POOL_SIZE);
		Log.info("服务启动，等待请求！");
		System.out.println("waiting for");
		// 注册定期运行任务
		Timer timer = new Timer();
		timer.schedule(new TimingTask(), 6000, 20000);
	}

	public void service() {
		while (true) {
			Socket socket = null;
			try {
				socket = serverSocket.accept();
				System.out.println("First get it " + socket.getInetAddress()
						+ ":" + socket.getPort());
				Log.info("收到用户的连接请求,地址为--" + socket.getInetAddress() + ":"
						+ socket.getPort());
				String sec = null;
				InputStream socketIn = socket.getInputStream();
				BufferedReader br = new BufferedReader(new InputStreamReader(
						socketIn));
				sec = br.readLine();
				//  判断是否为Ardnino登录
				SimpleDateFormat df = new SimpleDateFormat(
						"yyyy-MM-dd HH:mm:ss");// 设置日期格式
				System.out.println(df.format(new Date()));// new Date()为获取当前系统时间
				if (sec.length() == 32) {
					System.out.println("Sec:" + sec);
					ArduinoModle arduinoModel = new ArduinoModle(sec);
					MainHandler temr = new MainHandler(socket,
							arduinoModel.getGameuid(), false);
					executorService.execute(temr);
					arduinoHandler.put(MaxTem + "", temr);
				} else {
					MainHandler temr = new MainHandler(socket, MaxTem, true);
					executorService.execute(temr);
					System.out.println("put into tem:" + MaxTem);
					temHandler.put(MaxTem + "", temr);
					MaxTem++;
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println(e.toString());
				Log.error("用户发起连接出现异常", e);
			}
		}
	}

	public static boolean socketWriteTem(int id, String content) {
		if (id <= 0) {
			return false;
		}
		if (temHandler.containsKey(id + "")) {
			MainHandler tem = temHandler.get(id + "");
			try {
				OutputStream socketOut = tem.socket.getOutputStream();
				PrintWriter pw = new PrintWriter(socketOut, true);
				pw.println(content);
				Log.info(String.format("服务端写向Arduino客户端%d报文为：%s", id, content));
				// 存入缓存
				String key = id + content;
				Integer time = new Integer(
						(int) (System.currentTimeMillis() / 1000));
				Redis.set(key, time.toString());
				System.out.println("[send to client]" + content);
				return true;
			} catch (Exception e) {
				// 记录失败的程序
				e.printStackTrace();
				// 把socket给移除
				cleanSocket(id);
				System.out.println(String.format("[send to client %d error]",
						id));
			}
		} else {
			Log.info(String.format("在Android临时组里没有ID为%d的客户端", id));
		}
		return false;
	}

	public static boolean socketWriteTemArduino(int id, String content) {
		if (id <= 0) {
			return false;
		}
		if (arduinoHandler.containsKey(id + "")) {
			MainHandler tem = arduinoHandler.get(id + "");
			try {
				OutputStream socketOut = tem.socket.getOutputStream();
				PrintWriter pw = new PrintWriter(socketOut, true);
				pw.println(content);
				Log.info(String.format("服务端写向Arduino客户端%d报文为：%s", id, content));
				// 存入缓存
				String key = id + content;
				Integer time = new Integer(
						(int) (System.currentTimeMillis() / 1000));
				Redis.set(key, time.toString());
				System.out.println("[send to client arduino]" + content);
				return true;
			} catch (Exception e) {
				// 记录失败的程序
				e.printStackTrace();
				// 把socket给移除
				cleanSocket(id);
				System.out.println(String.format("[send to client %d error]",
						id));
			}
		} else {
			Log.info(String.format("在Arduino组里没有ID为%d的客户端", id));
		}
		return false;
	}

	/**
	 * 输出。
	 * 
	 * @param gameuid
	 * @param fromgameuid
	 * @param content
	 * @param resend
	 * @return
	 */
	public static boolean socketWrite(int id, int fromid, String content,
			boolean resend) {
		if (id <= 0) {
			return false;
		}
		if (globalHandler.containsKey(id + "")) {
			MainHandler temHandler = globalHandler.get(id + "");
			try {
				OutputStream socketOut = temHandler.socket.getOutputStream();
				PrintWriter pw = new PrintWriter(socketOut, true);
				pw.println(content);
				Log.info(String.format("服务端写向Android客户端%d报文为：%s", id, content));
				// 存入缓存
				String key = id + content;
				Integer time = new Integer(
						(int) (System.currentTimeMillis() / 1000));
				Redis.set(key, time.toString());
				System.out.println("[send to client]" + content);
				Log.info("服务启动，等待请求！");
				return true;
			} catch (Exception e) {
				// 记录失败的程序
				e.printStackTrace();
				// 把socket给移除
				cleanSocket(id);
				System.out.println(String.format("[send to client %d error]",
						id));
			}
		} else {
			Log.info(String.format("在Global组里没有ID为%d的客户端", id));
		}
		if (!resend) {
			Behave errorBehave = new Behave(0);
			errorBehave.newInfo(id, fromid, 0, content);
		}
		return false;
	}

	public static void cleanSocket(int id) {
		Socket socket = globalHandler.get(id + "").socket;
		try {
			socket.close();
			socket = null;
			globalHandler.remove(id + "");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 取得命令行，可以是手机，也可以是板子
	 * 
	 * @param gameuid
	 * @param content
	 * @return
	 */
	public static boolean socketRead(String content, int id, int fromid,
			boolean tem) {
		System.out.println("服务端收到的报文为：%s" + content);
		Log.info(String.format("服务端收到的客户端%d报文为：%s", id, content));
		MessageControl.MessageControl(content, id, fromid, tem);
		return true;
	}

	public static void main(String[] args) throws IOException {
		new Main().service();
	}

	public static boolean moveSocketInGlobal(String temName, int globalName) {
		temHandler.get(temName).tem = false;
		System.out.println("changeitnameto:" + globalName);
		temHandler.get(temName).id = globalName;
		temHandler.get(temName).fromid = globalName;
		globalHandler.put(globalName + "", temHandler.get(temName));
		temHandler.remove(temName);
		System.out.println("globalCount:" + globalHandler.size());
		System.out.println("temCount:" + temHandler.size());
		return true;
	}
}

